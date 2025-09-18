package com.example.demo.service.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.dto.chat.ChatMessageDTO;
import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.model.chat.EmotionContext;
import com.example.demo.model.chat.MemorySummary;
import com.example.demo.model.chat.ConversationState;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
import com.example.demo.repository.chat.UserPreferenceRepository.UserPreferenceRepository;
import com.example.demo.repository.chat.memory.MemorySummaryRepo;
import com.example.demo.service.chat.chunking.TokenCounterService;
import com.example.demo.service.chat.context.ContextCompressionService;
import com.example.demo.service.chat.emotion.EmotionAnalysisService;
import com.example.demo.service.chat.preference.UserPreferenceService;
import com.example.demo.service.chat.reranking.RerankingService;
import com.example.demo.service.chat.state.ConversationStateService;
import com.example.demo.service.chat.util.EmbeddingService;
import com.example.demo.service.chat.util.TokenManagementService;
import com.example.demo.service.chat.vector.VectorStoreService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.demo.service.chat.fallback.FallbackService;
import com.example.demo.service.chat.integration.OpenAIService;
import com.example.demo.service.chat.memory.HierarchicalMemoryManager;
import com.example.demo.service.chat.memory.MemorySummaryManager;
import com.example.demo.service.chat.memory.PromptBuilder;
import com.example.demo.service.chat.memory.RedisChatMemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAIService {
    
    private final RedisChatMemoryService redisChatMemoryService;
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageService messageService;
    private final MemorySummaryManager memoryManager;
    private final PromptBuilder promptBuilder;
    private final OpenAIService openAIService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    // Thêm dependency
    private final RerankingService rerankingService;
    
    // ✅ CÁC SERVICES MỚI
    private final EmotionAnalysisService emotionAnalysisService;
    private final UserPreferenceService userPreferenceService;
    private final ConversationStateService conversationStateService;
    private final FallbackService fallbackService;
    
    // ✅ REPOSITORIES
    private final EmotionContextRepository emotionContextRepository;
    private final ConversationStateRepository conversationStateRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    
    private final TokenCounterService tokenCounterService;
    
    private final HierarchicalMemoryManager hierarchicalMemoryManager;
    
    private final MemorySummaryRepo summaryRepo; // Repository quản lý MemorySummary
    

    public String processMessages(Long sessionId, List<ChatMessageDTO> messageDTOs, User user) {
        String prompt = "";
        try {
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

            ChatMessageDTO latest = messageDTOs.get(messageDTOs.size() - 1);
            prompt = latest.getContent();

            List<ChatMessage> recentMessages = messageService.getRecentMessages(sessionId, 5);

            final String finalPrompt = prompt;
            final ChatSession finalSession = session;

            // ✅ PARALLEL PROCESSING với timeout riêng cho từng task
            CompletableFuture<EmotionContext> emotionFuture = CompletableFuture
                    .supplyAsync(() -> processEmotion(finalSession, user, finalPrompt));

            CompletableFuture<ConversationState> stateFuture = CompletableFuture
                    .supplyAsync(() -> processConversationState(sessionId, finalPrompt, recentMessages));

            CompletableFuture<Void> preferenceFuture = CompletableFuture
                    .runAsync(() -> userPreferenceService.detectAndUpdatePreferences(user.getId(), finalPrompt, ""));

            // ✅ XỬ LÝ TIMEOUT RIÊNG CHO TỪNG TASK
            EmotionContext emotionContext;
            ConversationState state;

            try {
                emotionContext = emotionFuture.get(800, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Emotion analysis timeout, using default");
                emotionContext = new EmotionContext();
            }

            try {
                state = stateFuture.get(800, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Conversation state timeout, using default");
                state = new ConversationState();
            }

            try {
                preferenceFuture.get(300, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Preference detection timeout");
            }

            // ✅ SMART RETRIEVAL - SỬ DỤNG HYBRID APPROACH
            List<ChatMessage> retrievalMessages = performHybridRetrieval(
                sessionId, finalPrompt, user, recentMessages
            );

            // Ghép retrievalMessages vào recentMessages nếu có
            List<ChatMessage> promptMessages = new ArrayList<>(recentMessages);
            if (!retrievalMessages.isEmpty()) {
                promptMessages.addAll(0, retrievalMessages);
                log.info("✅ Added {} retrieval messages to context", retrievalMessages.size());
            }

            // Tối ưu intent tham chiếu (giữ nguyên)
            if (isReferenceIntent(finalPrompt)) {
                List<String> userQuestions = redisChatMemoryService.getUserQuestions(sessionId);
                List<String> aiAnswers = redisChatMemoryService.getAIAnswers(sessionId);

                if (!userQuestions.isEmpty() && !aiAnswers.isEmpty()) {
                    int lastIdx = userQuestions.size() - 2;
                    if (lastIdx >= 0) {
                        promptMessages.add(0, new ChatMessage("user", userQuestions.get(lastIdx)));
                        promptMessages.add(1, new ChatMessage("assistant", aiAnswers.get(lastIdx)));
                    } else {
                        int bestIdx = 0;
                        double bestSim = -1.0;
                        List<Double> promptEmbedding = embeddingService.getEmbedding(finalPrompt);
                        for (int i = 0; i < userQuestions.size(); i++) {
                            double sim = embeddingService.cosineSimilarity(
                                embeddingService.getEmbedding(userQuestions.get(i)),
                                promptEmbedding
                            );
                            if (sim > bestSim) {
                                bestSim = sim;
                                bestIdx = i;
                            }
                        }
                        if (bestSim > 0.7) {
                            promptMessages.add(0, new ChatMessage("user", userQuestions.get(bestIdx)));
                            promptMessages.add(1, new ChatMessage("assistant", aiAnswers.get(bestIdx)));
                        }
                    }
                }
            }


            // Kiểm tra và cập nhật bộ nhớ tóm tắt nếu cần
            if (memoryManager.shouldUpdateMemory(finalSession, recentMessages)) {
                memoryManager.updateSummary(finalSession, recentMessages);
            }

            // ✅ Build prompt với context đã có - FIXED SIGNATURE
            List<Map<String, String>> fullPrompt = buildEnhancedPrompt(
                promptMessages, finalPrompt, finalSession,
                emotionContext, state, user, retrievalMessages // ✅ Chỉ truyền retrievalMessages
            );
            
            // ✅ Kiểm tra token limit và cắt bớt nếu cần
            TokenManagementService tokenService = new TokenManagementService(tokenCounterService);
            if (tokenService.willExceedTokenLimit(fullPrompt, 4000)) {
                fullPrompt = tokenService.truncateMessages(fullPrompt, 4000);
                log.warn("Prompt vượt quá token limit, đã cắt bớt. Số message còn lại: {}", fullPrompt.size() - 2);
            }


            // ✅ Lưu tin nhắn user (không chờ)
            CompletableFuture.runAsync(() -> {
                try {
                    messageService.saveMessage(finalSession, "user", finalPrompt);
                } catch (Exception e) {
                    log.error("Lỗi lưu message user: {}", e.getMessage());
                }
            });
            
            // ✅ GỌI AI - Bước quan trọng nhất
            String reply = openAIService.getChatCompletion(fullPrompt, "gpt-4o", 1500);

            // ✅ Cập nhật satisfaction (không chờ)
            CompletableFuture.runAsync(() -> updateSatisfactionScore(sessionId, finalPrompt, reply));

            // ✅ Lưu tin nhắn AI và cập nhật cache (không chờ)
            CompletableFuture.runAsync(() -> {
                try {
                    messageService.saveMessage(finalSession, "assistant", reply);
                    redisChatMemoryService.saveUserQuestion(sessionId, finalPrompt);
                    redisChatMemoryService.saveAIAnswer(sessionId, reply);
                    userPreferenceService.detectAndUpdatePreferences(user.getId(), finalPrompt, reply);
                } catch (Exception e) {
                    log.error("Lỗi lưu dữ liệu hậu xử lý: {}", e.getMessage());
                }
            });

            return reply;

        } catch (TimeoutException e) {
            log.warn("Parallel processing timeout, using fallback");
            return fallbackService.getFallbackResponse(prompt, "timeout");
        } catch (Exception e) {
            log.error("Lỗi xử lý processMessages: {}", e.getMessage());
            return fallbackService.getEmergencyResponse();
        }
    }
    
    private List<ChatMessage> performHybridRetrieval(Long sessionId, String prompt, 
            User user, List<ChatMessage> recentMessages) {

        long startTime = System.currentTimeMillis();
        
        // 1. Phân loại query để chọn strategy
        SearchStrategy strategy = classifyQuery(prompt);
        
        List<ChatMessage> results;
        String methodUsed;
        
        // 2. Detect topic for pre-filtering
        String detectedTopic = detectTopicForQuery(prompt);
        
        switch (strategy) {
            case KEYWORD:
                results = vectorStoreService.findKeywordMessages(sessionId, prompt, 20);
                methodUsed = "keyword-only";
                break;
                
            case SEMANTIC:
                results = vectorStoreService.findSimilarMessagesWithFilters(
                    sessionId, prompt, 20, null, detectedTopic, null, null);
                methodUsed = "semantic-with-topic-filter";
                break;
                
            case HYBRID:
            default:
                if (isUserQuery(prompt) && detectedTopic != null) {
                    LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
                    results = vectorStoreService.findSimilarMessagesWithFilters(
                        sessionId, prompt, 20, "user", detectedTopic, oneWeekAgo, LocalDateTime.now());
                    methodUsed = "hybrid-with-user-topic-time-filter";
                } else {
                    results = vectorStoreService.findHybridMessages(sessionId, prompt, 20);
                    methodUsed = "hybrid";
                }
                break;
        }
        
        // Đảm bảo results không null
        if (results == null) {
            results = new ArrayList<>();
        }
        
        // 3. Áp dụng re-ranking
        List<ChatMessage> rerankedResults;
        
        // Chọn strategy re-ranking dựa trên loại query
        boolean isComplex = isComplexQuery(prompt);
        boolean isTechnical = isTechnicalQuery(prompt);
        
        log.debug("Query analysis - Complex: {}, Technical: {}, Query: '{}'", 
                 isComplex, isTechnical, prompt);
        
        if (isComplex || isTechnical) {
            // Sử dụng hybrid re-ranking cho query phức tạp hoặc kỹ thuật
            Map<String, Double> weights = getHybridWeightsBasedOnQueryType(prompt);
            rerankedResults = rerankingService.hybridRerank(prompt, results, weights, 5);
            log.debug("Using hybrid re-ranking with weights: {}", weights);
        } else {
            // Sử dụng re-ranking thông thường
            rerankedResults = rerankingService.rerankResults(prompt, results, 5);
            log.debug("Using standard re-ranking");
        }
        
        // Đảm bảo rerankedResults không null
        if (rerankedResults == null) {
            rerankedResults = new ArrayList<>();
        }
        
        // 4. Loại bỏ trùng lặp với recent messages
        List<ChatMessage> uniqueMessages = removeDuplicates(rerankedResults, recentMessages);
        
        long duration = System.currentTimeMillis() - startTime;
        logRetrievalPerformance(sessionId, prompt, uniqueMessages, duration, 
                              methodUsed + "→reranked");
        
        return uniqueMessages;
    }
    
    private boolean isComplexQuery(String query) {
        // Query có nhiều từ hoặc chứa từ khóa phức tạp
        return query.split("\\s+").length > 5 || 
               query.contains("?") || 
               query.contains("how") || 
               query.contains("why");
    }

    private Map<String, Double> getHybridWeightsBasedOnQueryType(String query) {
        if (query.contains("recent") || query.contains("mới nhất")) {
            return Map.of("semantic", 0.4, "recency", 0.4, "keyword", 0.2);
        } else if (isTechnicalQuery(query)) {
            return Map.of("semantic", 0.6, "recency", 0.2, "keyword", 0.2);
        } else {
            return Map.of("semantic", 0.5, "recency", 0.3, "keyword", 0.2);
        }
    }
    
 // Thêm cache cho technical query detection
    private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();

    private boolean isTechnicalQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        
        // Kiểm tra cache trước
        return technicalQueryCache.get(query, this::analyzeTechnicalQuery);
    }
    
    private boolean analyzeTechnicalQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Danh sách từ khóa kỹ thuật phổ biến (tiếng Anh và tiếng Việt)
        String[] technicalKeywords = {
            // Programming concepts
            "java", "python", "javascript", "c++", "c#", "php", "ruby", "go", "rust", "swift",
            "code", "programming", "lập trình", "mã", "source code", 
            "function", "hàm", "method", "phương thức", "class", "lớp", "object", "đối tượng",
            "variable", "biến", "constant", "hằng số", "loop", "vòng lặp", "condition", "điều kiện",
            "algorithm", "thuật toán", "data structure", "cấu trúc dữ liệu",
            "api", "rest", "graphql", "endpoint", "microservice", "microservices",
            "database", "cơ sở dữ liệu", "sql", "nosql", "mysql", "postgresql", "mongodb",
            "redis", "elasticsearch", "orm", "hibernate", "jpa",
            
            // Error and debugging
            "error", "lỗi", "exception", "ngoại lệ", "bug", "debug", "gỡ lỗi", "stack trace",
            "compile", "biên dịch", "runtime", "thời gian chạy",
            
            // Technical terms
            "framework", "thư viện", "library", "dependency", "phụ thuộc",
            "git", "github", "gitlab", "version control", "kiểm soát phiên bản",
            "docker", "container", "kubernetes", "k8s", "deployment", "triển khai",
            "server", "máy chủ", "client", "máy khách", "http", "https", "protocol", "giao thức",
            "security", "bảo mật", "authentication", "xác thực", "authorization", "ủy quyền",
            "oauth", "jwt", "token",
            
            // Development tools and processes
            "ide", "intellij", "eclipse", "vscode", "visual studio",
            "agile", "scrum", "kanban", "ci/cd", "continuous integration", "tích hợp liên tục",
            "test", "kiểm thử", "unit test", "integration test", "kiểm thử tích hợp",
            "refactor", "tái cấu trúc", "optimize", "tối ưu hóa", "performance", "hiệu năng",
            
            // Web technologies
            "html", "css", "bootstrap", "tailwind", "react", "angular", "vue", "node.js",
            "spring", "spring boot", "django", "flask", "laravel",
            
            // System design
            "architecture", "kiến trúc", "design pattern", "mẫu thiết kế",
            "singleton", "factory", "observer", "strategy", "decorator",
            "scalability", "khả năng mở rộng", "reliability", "độ tin cậy",
            "load balancing", "cân bằng tải", "caching", "bộ nhớ đệm"
        };
        
        // Kiểm tra từ khóa kỹ thuật
        for (String keyword : technicalKeywords) {
            if (lowerQuery.contains(keyword)) {
                log.debug("Query được nhận diện là technical: '{}' chứa keyword '{}'", query, keyword);
                return true;
            }
        }
        
        // Kiểm tra các pattern đặc biệt của query kỹ thuật
        if (containsTechnicalPatterns(lowerQuery)) {
            log.debug("Query được nhận diện là technical theo pattern: '{}'", query);
            return true;
        }
        
        return false;
    }

    private boolean containsTechnicalPatterns(String query) {
        // Pattern 1: Câu hỏi về lỗi hoặc exception
        if (query.matches(".*(error|exception|lỗi|bug).*(in|trong|with|với).*") ||
            query.matches(".*how to fix.*(error|exception|lỗi|bug).*") ||
            query.matches(".*cách sửa.*(lỗi|bug).*")) {
            return true;
        }
        
        // Pattern 2: Câu hỏi về cú pháp hoặc implementation
        if (query.matches(".*how to (implement|use|create).*") ||
            query.matches(".*cách (triển khai|sử dụng|tạo).*") ||
            query.matches(".*syntax for.*") ||
            query.matches(".*cú pháp.*")) {
            return true;
        }
        
        // Pattern 3: Câu hỏi về best practice hoặc optimization
        if (query.matches(".*best practice.*") ||
            query.matches(".*best way to.*") ||
            query.matches(".*cách tốt nhất.*") ||
            query.matches(".*how to optimize.*") ||
            query.matches(".*cách tối ưu.*")) {
            return true;
        }
        
        // Pattern 4: Câu hỏi về version hoặc compatibility
        if (query.matches(".*version.*compatibility.*") ||
            query.matches(".*tương thích.*phiên bản.*") ||
            query.matches(".*which version.*") ||
            query.matches(".*phiên bản nào.*")) {
            return true;
        }
        
        return false;
    }
    
    // ✅ TOPIC DETECTION FOR QUERY
    private String detectTopicForQuery(String query) {
        if (query == null || query.length() < 5) return null;
        
        try {
            // Simple keyword matching first
            String lowerQuery = query.toLowerCase();
            
            if (lowerQuery.contains("java") || lowerQuery.contains("code") || 
                lowerQuery.contains("program") || lowerQuery.contains("lập trình")) {
                return "programming";
            }
            
            if (lowerQuery.contains("weather") || lowerQuery.contains("thời tiết") || 
                lowerQuery.contains("nhiệt độ")) {
                return "weather";
            }
            
            if (lowerQuery.contains("music") || lowerQuery.contains("nhạc") || 
                lowerQuery.contains("bài hát")) {
                return "music";
            }
            
            // For more complex queries, use AI detection
            if (query.length() > 20) {
                return detectTopicWithAI(query);
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Query topic detection failed: {}", e.getMessage());
            return null;
        }
    }
    
 // ✅ THÊM PHƯƠNG THỨC detectTopicWithAI VÀO ChatAIService
    private String detectTopicWithAI(String content) {
        try {
            List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", 
                    "Phân tích đoạn văn và trả về 1 từ khóa chủ đề duy nhất. " +
                    "Chỉ trả về từ khóa, không giải thích. " +
                    "Các chủ đề phổ biến: programming, weather, music, sports, food, general."),
                Map.of("role", "user", "content", 
                    "Xác định chủ đề cho đoạn văn sau: " + content.substring(0, Math.min(200, content.length())))
            );
            
            String topic = openAIService.getChatCompletion(messages, "gpt-3.5-turbo", 10);
            return topic != null ? topic.trim().toLowerCase() : "general";
        } catch (Exception e) {
            log.warn("AI topic detection failed, using fallback: {}", e.getMessage());
            return "general";
        }
    }
    
    // ✅ CHECK IF QUERY IS FROM USER PERSPECTIVE
    private boolean isUserQuery(String query) {
        if (query == null) return false;
        String lowerQuery = query.toLowerCase();
        
        return lowerQuery.contains("tôi") || lowerQuery.contains("mình") || 
               lowerQuery.contains("tớ") || lowerQuery.contains("tao") ||
               lowerQuery.startsWith("how do i") || lowerQuery.startsWith("how can i");
    }
    
    private SearchStrategy classifyQuery(String query) {
        if (query == null || query.isBlank()) return SearchStrategy.HYBRID;
        
        String lowerQuery = query.toLowerCase();
        
        // Query ngắn và có từ khóa kỹ thuật → ưu tiên keyword
        if (query.length() < 20 && containsTechnicalKeywords(lowerQuery)) {
            return SearchStrategy.KEYWORD;
        }
        
        // Query dài và phức tạp → ưu tiên semantic
        if (query.length() > 50 || isComplexNaturalLanguage(lowerQuery)) {
            return SearchStrategy.SEMANTIC;
        }
        
        // Mặc định dùng hybrid
        return SearchStrategy.HYBRID;
    }
    
    private boolean containsTechnicalKeywords(String query) {
        // Danh sách từ khóa kỹ thuật phổ biến
        String[] techKeywords = {"exception", "error", "function", "method", "class", 
                               "interface", "api", "syntax", "compile", "runtime"};
        
        for (String keyword : techKeywords) {
            if (query.contains(keyword)) return true;
        }
        return false;
    }
    
    private boolean isComplexNaturalLanguage(String query) {
        // Query có nhiều từ và cấu trúc phức tạp
        String[] words = query.split("\\s+");
        return words.length > 8 && query.contains("?") && 
               (query.contains("how") || query.contains("why") || query.contains("what"));
    }
    
    private enum SearchStrategy {
        KEYWORD, SEMANTIC, HYBRID
    }
    
    private void logRetrievalPerformance(Long sessionId, String prompt, 
            List<ChatMessage> retrievedMessages, 
            long durationMs, String method) {

log.info("📊 Retrieval - Session: {}, Method: {}, Messages: {}, Time: {}ms",
sessionId, method, retrievedMessages.size(), durationMs);

if (!retrievedMessages.isEmpty()) {
log.debug("Retrieved messages for prompt: '{}'", prompt);
for (ChatMessage msg : retrievedMessages) {
log.debug("  - {}: {}", msg.getSender(), msg.getContent());
}
}
}
    
    private boolean isExplicitRecallQuestion(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("nhắc lại") || lower.contains("trước đó") || 
               lower.contains("vừa nói") || lower.contains("vừa hỏi") || 
               lower.contains("nói lại") || lower.contains("kể lại");
    }

    private List<ChatMessage> filterBySimilarityThreshold(List<ChatMessage> messages, 
                                                         String prompt, double threshold) {
        if (messages.isEmpty()) return List.of();
        
        List<ChatMessage> filtered = new ArrayList<>();
        for (ChatMessage msg : messages) {
            try {
                double similarity = embeddingService.cosineSimilarity(
                    embeddingService.getEmbedding(msg.getContent()),
                    embeddingService.getEmbedding(prompt)
                );
                if (similarity > threshold) {
                    filtered.add(msg);
                }
            } catch (Exception e) {
                log.warn("Error calculating similarity for message {}: {}", msg.getId(), e.getMessage());
            }
        }
        return filtered;
    }

    private List<ChatMessage> removeDuplicates(List<ChatMessage> retrieved, 
            List<ChatMessage> recent) {
if (retrieved == null) retrieved = new ArrayList<>();
if (recent == null) recent = new ArrayList<>();

Set<Long> recentIds = recent.stream()
.map(ChatMessage::getId)
.filter(id -> id != null)
.collect(Collectors.toSet());

return retrieved.stream()
.filter(msg -> msg != null && msg.getId() != null)
.filter(msg -> !recentIds.contains(msg.getId()))
.collect(Collectors.toList());
}

    private List<ChatMessage> getFallbackMessages(Long sessionId, int limit) {
        try {
            return messageService.getRecentMessages(sessionId, limit);
        } catch (Exception e) {
            log.warn("Fallback message retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }
    
    // ✅ Helper methods for parallel processing
    private EmotionContext processEmotion(ChatSession session, User user, String prompt) {
        try {
            EmotionContext emotionContext = emotionContextRepository.findByChatSession_Id(session.getId())
                .orElseGet(() -> createNewEmotionContext(session, user));
            emotionContext = emotionAnalysisService.analyzeEmotion(prompt, emotionContext);
            
            // Đảm bảo không có giá trị null
            if (emotionContext.getCurrentEmotion() == null) {
                emotionContext.setCurrentEmotion("neutral");
            }
            if (emotionContext.getEmotionIntensity() == null) {
                emotionContext.setEmotionIntensity(0.5);
            }
            
            return emotionContextRepository.save(emotionContext);
        } catch (Exception e) {
            log.warn("Emotion analysis failed: {}", e.getMessage());
            // Return default với giá trị không null
            EmotionContext defaultContext = new EmotionContext();
            defaultContext.setCurrentEmotion("neutral");
            defaultContext.setEmotionIntensity(0.5);
            return defaultContext;
        }
    }

    private ConversationState processConversationState(Long sessionId, String prompt, List<ChatMessage> recentMessages) {
        try {
            ConversationState state = conversationStateService.getOrCreateState(sessionId);
            updateConversationState(state, prompt, recentMessages);
            
            // Đảm bảo không có giá trị null
            if (state.getConversationStage() == null) {
                state.setConversationStage("main");
            }
            if (state.getCurrentTopic() == null) {
                state.setCurrentTopic("general");
            }
            if (state.getFrustrationLevel() == null) {
                state.setFrustrationLevel(0);
            }
            if (state.getSatisfactionScore() == null) {
                state.setSatisfactionScore(5);
            }
            
            return conversationStateRepository.save(state);
        } catch (Exception e) {
            log.warn("Conversation state update failed: {}", e.getMessage());
            // Return default với giá trị không null
            ConversationState defaultState = new ConversationState();
            defaultState.setConversationStage("main");
            defaultState.setCurrentTopic("general");
            defaultState.setFrustrationLevel(0);
            defaultState.setSatisfactionScore(5);
            return defaultState;
        }
    }

    // ✅ CÁC PHƯƠNG THỨC TRỢ GIÚP KHÁC GIỮ NGUYÊN...

    private EmotionContext createNewEmotionContext(ChatSession session, User user) {
        EmotionContext context = new EmotionContext();
        context.setChatSession(session);
        context.setUser(user);
        context.setCurrentEmotion("neutral");
        context.setEmotionIntensity(0.5);
        return context;
    }

    private void updateConversationState(ConversationState state, String prompt, List<ChatMessage> recentMessages) {
        // Phát hiện stage mới dựa trên content
        String newStage = detectConversationStage(prompt, recentMessages);
        String newTopic = detectCurrentTopic(prompt);
        
        // Đảm bảo không null
        String currentStage = state.getConversationStage() != null ? state.getConversationStage() : "main";
        String currentTopic = state.getCurrentTopic() != null ? state.getCurrentTopic() : "general";
        
        if (!newStage.equals(currentStage) || !newTopic.equals(currentTopic)) {
            if (state.getStateHistory() == null) {
                state.setStateHistory(new ArrayList<>());
            }
            state.getStateHistory().add(currentStage);
            state.setConversationStage(newStage);
            state.setCurrentTopic(newTopic);
            state.setLastStateChange(LocalDateTime.now());
        }

        // Adjust frustration level based on message characteristics
        if (isFrustratedMessage(prompt)) {
            Integer currentFrustration = state.getFrustrationLevel() != null ? state.getFrustrationLevel() : 0;
            conversationStateService.adjustFrustrationLevel(state.getChatSession().getId(), currentFrustration + 1);
        }
    }

    private String detectConversationStage(String prompt, List<ChatMessage> recentMessages) {
        String lowerPrompt = prompt.toLowerCase();
        
        if (recentMessages.isEmpty() || lowerPrompt.contains("xin chào") || lowerPrompt.contains("hello")) {
            return "greeting";
        }
        if (lowerPrompt.contains("cảm ơn") || lowerPrompt.contains("tạm biệt") || lowerPrompt.contains("bye")) {
            return "closing";
        }
        if (lowerPrompt.contains("?") || lowerPrompt.contains("giải thích") || lowerPrompt.contains("tại sao")) {
            return "question";
        }
        if (recentMessages.size() > 3 && lowerPrompt.length() > 20) {
            return "main";
        }
        
        return "main";
    }

    private String detectCurrentTopic(String prompt) {
        // Simple topic detection - can be enhanced
        if (prompt.contains("java") || prompt.contains("code") || prompt.contains("lập trình")) {
            return "programming";
        }
        if (prompt.contains("thời tiết") || prompt.contains("nhiệt độ")) {
            return "weather";
        }
        if (prompt.contains("âm nhạc") || prompt.contains("bài hát")) {
            return "music";
        }
        return "general";
    }

    private boolean isFrustratedMessage(String prompt) {
        String lower = prompt.toLowerCase();
        return lower.contains("!!!") || lower.contains("??") || 
               lower.contains("không hiểu") || lower.contains("sao vậy") ||
               lower.length() < 5 && lower.contains("?");
    }

    // ✅ FIXED METHOD SIGNATURE
    private List<Map<String, String>> buildEnhancedPrompt(List<ChatMessage> messages, String currentPrompt,
            ChatSession session, EmotionContext emotionContext, ConversationState state, User user,
            List<ChatMessage> retrievalMessages) {

        // Get user preferences
        Map<String, Object> userPreferences = userPreferenceService.getUserPreferencesForPrompt(user.getId());

        // Get hierarchical context
        int currentSegment = memoryManager.getCurrentSegment(session); // Thêm phương thức này trong MemorySummaryManager
        String hierarchicalContext = hierarchicalMemoryManager.getHierarchicalContext(session, currentSegment, currentPrompt);

        // Build enhanced system prompt với hierarchical context
        String systemPrompt = buildSystemPromptWithContext(
            emotionContext, state, userPreferences, hierarchicalContext
        );

        List<Map<String, String>> enhancedPrompt = new ArrayList<>();
        enhancedPrompt.add(Map.of("role", "system", "content", systemPrompt));

        // Thêm retrieval context nếu có
        if (!retrievalMessages.isEmpty()) {
            String context = buildRetrievalContext(retrievalMessages);
            enhancedPrompt.add(Map.of("role", "system", "content", 
                "THÔNG TIN NGỮ CẢNH TỪ CUỘC TRÒ CHUYỆN TRƯỚC ĐÂY:\n" + context));
        }

        // Add conversation history
        for (ChatMessage msg : messages) {
            enhancedPrompt.add(Map.of(
                "role", msg.getSender().toLowerCase(),
                "content", msg.getContent()
            ));
        }

        // Add current prompt
        enhancedPrompt.add(Map.of("role", "user", "content", currentPrompt));

        return enhancedPrompt;
    }
    
    private String buildRetrievalContext(List<ChatMessage> retrievedMessages) {
        StringBuilder context = new StringBuilder();
        for (ChatMessage msg : retrievedMessages) {
            context.append(msg.getSender()).append(": ")
                   .append(msg.getContent()).append("\n");
        }
        return context.toString();
    }

    private String buildSystemPromptWithContext(EmotionContext emotionContext, ConversationState state,
            Map<String, Object> userPreferences, String hierarchicalContext) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là trợ lý AI thông minh. ");

        // Thêm hierarchical context nếu có
        if (hierarchicalContext != null && !hierarchicalContext.isEmpty()) {
            prompt.append("Dưới đây là ngữ cảnh phân cấp từ cuộc trò chuyện:\n")
                  .append(hierarchicalContext)
                  .append("\nHãy sử dụng thông tin này để hiểu ngữ cảnh tổng quan.\n\n");
        }

        // Add emotion context - FIX NULL CHECK
        if (emotionContext != null && emotionContext.getCurrentEmotion() != null) {
            Double intensity = emotionContext.getEmotionIntensity();
            double safeIntensity = intensity != null ? intensity : 0.5; // Default value
            
            prompt.append(String.format("Người dùng đang có cảm xúc %s (cường độ %.1f). ",
                emotionContext.getCurrentEmotion(), safeIntensity));

            if (safeIntensity > 0.7) {
                prompt.append("Hãy phản hồi với sự đồng cảm. ");
            }
        }

        // Add conversation state - FIX NULL CHECK
        if (state != null) {
            String stage = state.getConversationStage() != null ? state.getConversationStage() : "unknown";
            String topic = state.getCurrentTopic() != null ? state.getCurrentTopic() : "general";
            
            prompt.append(String.format("Cuộc trò chuyện đang ở giai đoạn %s, chủ đề %s. ",
                stage, topic));

            Integer frustrationLevel = state.getFrustrationLevel();
            if (frustrationLevel != null && frustrationLevel > 5) {
                prompt.append("Người dùng đang hơi khó chịu, hãy trả lời cẩn thận và rõ ràng. ");
            }
        }

        // Add user preferences từ Map - FIX NULL CHECK
        if (userPreferences != null && !userPreferences.isEmpty()) {
            String style = userPreferences.get("communicationStyle") != null ? 
                userPreferences.get("communicationStyle").toString() : "neutral";
            String detail = userPreferences.get("detailPreference") != null ? 
                userPreferences.get("detailPreference").toString() : "medium";
            
            prompt.append(String.format("Người dùng thích phong cách %s, mức độ chi tiết %s. ",
                style, detail));
        }

        prompt.append("Hãy trả lời tự nhiên và hữu ích.");
        return prompt.toString();
    }
    
    public List<Map<String, String>> buildPromptWithHierarchicalMemory(
            List<ChatMessage> messages, String currentPrompt, 
            ChatSession session, int currentSegment) {
        
        String hierarchicalContext = hierarchicalMemoryManager
                .getHierarchicalContext(session, currentSegment, currentPrompt);
        
        List<Map<String, String>> prompt = new ArrayList<>();
        
        prompt.add(Map.of("role", "system", "content", 
            "Bạn là trợ lý AI thông minh. Dưới đây là ngữ cảnh phân cấp từ cuộc trò chuyện:\n" +
            hierarchicalContext +
            "\nHãy sử dụng thông tin này để hiểu ngữ cảnh tổng quan."));
        
        // Thêm conversation history
        for (ChatMessage msg : messages) {
            prompt.add(Map.of(
                "role", msg.getSender().toLowerCase(),
                "content", msg.getContent()
            ));
        }
        
        prompt.add(Map.of("role", "user", "content", currentPrompt));
        
        return prompt;
    }

    private void updateSatisfactionScore(Long sessionId, String userPrompt, String aiResponse) {
        // Simple satisfaction scoring based on response quality
        int scoreChange = 0;
        
        if (aiResponse == null || aiResponse.length() < 20) {
            scoreChange = -1; // Response too short
        } else if (aiResponse.length() > 300) {
            scoreChange = 1; // Detailed response
        }
        
        if (aiResponse != null && (aiResponse.contains("xin lỗi") || aiResponse.contains("không hiểu"))) {
            scoreChange = -2; // AI confused
        }
        
        if (scoreChange != 0) {
            ConversationState state = conversationStateService.getOrCreateState(sessionId);
            Integer currentScore = state.getSatisfactionScore() != null ? state.getSatisfactionScore() : 5;
            int newScore = Math.min(Math.max(currentScore + scoreChange, 1), 10);
            state.setSatisfactionScore(newScore);
            conversationStateRepository.save(state);
        }
    }

    // Hàm nhận diện câu hỏi dạng nhắc lại/thông tin cũ
    private boolean isRecallQuestion(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("nhắc lại") || lower.contains("trước đó") || 
               lower.contains("vừa nói") || lower.contains("vừa hỏi") || 
               lower.contains("trả lời lại") || lower.contains("nội dung cũ") || 
               lower.contains("thông tin cũ");
    }

    // Hàm nhận diện intent tham chiếu ngữ cảnh
    private boolean isReferenceIntent(String prompt) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("tại sao") || lower.contains("ý bạn") || 
               lower.contains("cái đó") || lower.contains("đó là gì") || 
               lower.contains("vì sao") || lower.contains("liên quan") || 
               lower.contains("vừa nói") || lower.contains("trước đó");
    }

    private List<Map<String, String>> filterAndSortMessages(List<ChatMessage> messages, String currentPrompt) {
        // Ưu tiên các message có độ liên quan cao đến current prompt
        return messages.stream()
            .map(msg -> {
                double relevance = calculateRelevance(msg.getContent(), currentPrompt);
                return Map.of(
                    "message", msg,
                    "relevance", relevance,
                    "timestamp", msg.getTimestamp()
                );
            })
            .sorted((a, b) -> {
                // Ưu tiên relevance cao hơn
                double relevanceDiff = (Double)b.get("relevance") - (Double)a.get("relevance");
                if (Math.abs(relevanceDiff) > 0.2) {
                    return relevanceDiff > 0 ? 1 : -1;
                }
                // Sau đó ưu tiên message mới hơn
                return ((LocalDateTime)b.get("timestamp")).compareTo((LocalDateTime)a.get("timestamp"));
            })
            .map(entry -> Map.of(
                "role", ((ChatMessage)entry.get("message")).getSender().toLowerCase(),
                "content", ((ChatMessage)entry.get("message")).getContent()
            ))
            .collect(Collectors.toList());
    }

    private double calculateRelevance(String content, String query) {
        try {
            List<Double> contentEmbedding = embeddingService.getEmbedding(content);
            List<Double> queryEmbedding = embeddingService.getEmbedding(query);
            return embeddingService.cosineSimilarity(contentEmbedding, queryEmbedding);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    
}