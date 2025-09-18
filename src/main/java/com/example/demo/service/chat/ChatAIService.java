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

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
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
//import com.example.demo.service.chat.util.SpringAIEmbeddingService;
//import com.example.demo.service.chat.util.EmbeddingService;
import com.example.demo.service.chat.util.TokenManagementService;
//import com.example.demo.service.chat.vector.VectorStoreService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;

import com.example.demo.service.chat.fallback.FallbackService;
//import com.example.demo.service.chat.integration.OpenAIService;
//import com.example.demo.service.chat.integration.SpringAIChatService;
import com.example.demo.service.chat.memory.HierarchicalMemoryManager;
import com.example.demo.service.chat.memory.MemorySummaryManager;
//import com.example.demo.service.chat.memory.PromptBuilder;
import com.example.demo.service.chat.memory.RedisChatMemoryService;
import com.example.demo.service.chat.memory.langchain.ConversationSummaryService;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.Map;
import java.util.stream.Collectors;

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
    //private final PromptBuilder promptBuilder;
    //private final OpenAIService openAIService;
    //private final SpringAIChatService springAIChatService;
    //private final SpringAIEmbeddingService embeddingService;
    //private final EmbeddingService embeddingService;
    //private final VectorStoreService vectorStoreService;
    // Thêm dependency
    //private final RerankingService rerankingService;
    
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
    
    private final LangChainChatMemoryService langChainChatMemoryService;
    private final ConversationSummaryService conversationSummaryService;
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    
    private final RerankingService rerankingService; // Chúng ta cần gọi trực tiếp
    private final LangChainChatMemoryService langChain4jMemoryService;
    // (Các dependency khác như embeddingStore, embeddingModel, userPreferenceService... giữ nguyên)


 // TRONG ChatAIService.java
    public String processMessages(Long sessionId, List<ChatMessageDTO> messageDTOs, User user) {
        try {
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

            String prompt = messageDTOs.get(messageDTOs.size() - 1).getContent();
            ChatMemory chatMemory = langChain4jMemoryService.getChatMemory(sessionId);

            if (chatMemory.messages().isEmpty()) {
                 log.debug("Chat memory for session {} is empty. Hydrating from database...", sessionId);
                 hydrateChatMemoryFromDB(chatMemory, sessionId);
            }

            // === KẾT NỐI BỘ NHỚ DÀI HẠN (Đã sửa ở lượt trước) ===
            int currentSegment = memoryManager.getCurrentSegment(session); 
            String longTermContext = hierarchicalMemoryManager.getHierarchicalContext(session, currentSegment, prompt);
            Map<String, Object> userPrefsMap = userPreferenceService.getUserPreferencesForPrompt(user.getId());
            runContextAnalysisAsync(session, user, prompt);

            
            // === BẮT ĐẦU PIPELINE ĐIỀU PHỐI (ORCHESTRATION) ===

            String ragContext = ""; // Context RAG (nếu có)
            String reply;           // Câu trả lời cuối cùng
            
            // 1. ✅ GIẢI QUYẾT LỖ HỔNG 3: ROUTER 3 TRẠNG THÁI
            QueryIntent intent = classifyQueryIntent(prompt);
            log.debug("Query intent classified as: {}", intent);

            if (intent == QueryIntent.CHITCHAT) {
                // Luồng 1: CHITCHAT (Bỏ qua RAG)
                log.debug("Handling as CHITCHAT. Skipping RAG.");
                // ragContext vẫn rỗng
                
                // Xây dựng prompt CHỈ với bộ nhớ
                 List<dev.langchain4j.data.message.ChatMessage> lcMessages = 
                        buildFinalLc4jMessages(chatMemory.messages(), ragContext, longTermContext, userPrefsMap, prompt);
                
                 // Gọi AI
                chatMemory.add(UserMessage.from(prompt));
                Response<AiMessage> response = chatLanguageModel.generate(lcMessages); 
                reply = response.content().text();
                chatMemory.add(AiMessage.from(reply));

            } else if (intent == QueryIntent.MEMORY_QUERY) {
                // Luồng 2: MEMORY_QUERY (Bỏ qua RAG, dùng hàm xử lý nhanh)
                log.debug("Handling as MEMORY_QUERY. Using direct memory handler.");
                reply = handleMemoryQuestion(chatMemory, prompt);
                
                // Thêm vào bộ nhớ tạm thời
                chatMemory.add(UserMessage.from(prompt));
                chatMemory.add(AiMessage.from(reply));
                
            } else {
                // Luồng 3: RAG_QUERY (Chạy pipeline RAG đầy đủ)
                log.debug("Handling as RAG_QUERY. Running full RAG pipeline.");
                
                Embedding queryEmbedding = embeddingModel.embed(prompt).content();

                // 2. ✅ GIẢI QUYẾT LỖ HỔNG 2: BỘ LỌC KẾT HỢP (Đã vá)
                Filter sessionMessageFilter = new IsEqualTo("sessionId", session.getId().toString());
                Filter userKnowledgeFilter = new And( // Dùng "new" vì import static bị lỗi
                    new IsEqualTo("userId", user.getId().toString()),
                    new IsEqualTo("docType", "knowledge") 
                );
                Filter finalFilter = new Or(sessionMessageFilter, userKnowledgeFilter); // Dùng "new"

                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(20) 
                        .filter(finalFilter) // Áp dụng bộ lọc bảo mật
                        .build();

                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
                List<EmbeddingMatch<TextSegment>> initialMatches = searchResult.matches();

                // 3. ✅ GIẢI QUYẾT VẤN ĐỀ 1: RERANKING LINH HOẠT
                List<EmbeddingMatch<TextSegment>> rerankedMatches;
                if (isTechnicalQuery(prompt)) {
                    // Nếu là câu hỏi kỹ thuật, dùng hybrid rerank (local) để ưu tiên từ khóa/độ mới
                    log.debug("Technical query detected. Using local HYBRID rerank.");
                    Map<String, Double> weights = Map.of("semantic", 0.4, "recency", 0.3, "keyword", 0.3);
                    rerankedMatches = rerankingService.hybridRerank(prompt, initialMatches, weights, 5);
                } else {
                    // Nếu là câu hỏi ngữ nghĩa phức tạp, gọi Cohere API
                    log.debug("Semantic query detected. Using COHERE rerank.");
                    rerankedMatches = rerankingService.rerankResults(prompt, initialMatches, 5);
                }

                ragContext = rerankedMatches.stream()
                        .map(match -> match.embedded().text())
                        .collect(Collectors.joining("\n---\n"));

                // Build prompt với RAG context
                List<dev.langchain4j.data.message.ChatMessage> lcMessages = 
                        buildFinalLc4jMessages(chatMemory.messages(), ragContext, longTermContext, userPrefsMap, prompt);
                
                // Gọi AI
                chatMemory.add(UserMessage.from(prompt));
                Response<AiMessage> response = chatLanguageModel.generate(lcMessages); 
                reply = response.content().text();
                chatMemory.add(AiMessage.from(reply));
            }

            // === LƯU TRỮ (LUÔN CHẠY) ===
            ChatMessage userMsgDb = messageService.saveMessage(session, "user", prompt);
            ChatMessage aiMsgDb = messageService.saveMessage(session, "assistant", reply);
            saveMessagesToVectorStore(userMsgDb, aiMsgDb, session); // Chạy bất đồng bộ
            
            return reply;

        } catch (Exception e) {
            log.error("Lỗi xử lý processMessages: {}", e.getMessage(), e);
            return fallbackService.getEmergencyResponse();
        }
    }
 
//Trong file: ChatAIService.java (Thêm vào cuối class)

 /**
  * Chạy bất đồng bộ để lưu cả tin nhắn của user và AI vào Vector Store (Embedding table)
  * để chúng có thể được truy xuất (retrieved) trong các lượt RAG tương lai.
  */
    @Async
 private void saveMessagesToVectorStore(ChatMessage userMessage, ChatMessage aiMessage, ChatSession session) {
     try {
         // Chuyển đổi cả hai tin nhắn sang TextSegments
         TextSegment userSegment = createSegmentFromMessage(userMessage, session);
         TextSegment aiSegment = createSegmentFromMessage(aiMessage, session);

         // Nhúng (embed) cả hai
         Embedding userEmbedding = embeddingModel.embed(userSegment).content();
         Embedding aiEmbedding = embeddingModel.embed(aiSegment).content();

         // Thêm cả hai vào embedding store
         embeddingStore.add(userEmbedding, userSegment);
         embeddingStore.add(aiEmbedding, aiSegment);
         
         log.debug("Đã lưu 2 tin nhắn (User: {}, AI: {}) vào vector store cho session {}", 
             userMessage.getId(), aiMessage.getId(), session.getId());

     } catch (Exception e) {
         // Chúng ta không muốn làm sập luồng chat chính nếu việc nhúng lỗi
         log.warn("Không thể lưu message embeddings vào vector store: {}", e.getMessage());
     }
 }

 /**
  * Helper để tạo một TextSegment từ ChatMessage (model DB)
  * để lưu trữ trong PgVectorEmbeddingStore.
  */
 private TextSegment createSegmentFromMessage(ChatMessage message, ChatSession session) {
     // Đây là nơi chúng ta thêm tất cả metadata mà RerankingService sẽ cần
     Metadata metadata = Metadata.from(Map.of(
         "messageId", message.getId().toString(),
         "sessionId", session.getId().toString(),
         "senderType", message.getSender(),
         "messageTimestamp", message.getTimestamp().toString() // Quan trọng cho hybrid rerank
         // "detectedTopic", "..." // (Bạn có thể thêm logic phát hiện topic ở đây)
     ));
     
     return TextSegment.from(message.getContent(), metadata);
 }
    
 // Trong file: ChatAIService.java (thêm vào cuối class)

    /**
     * Tải lịch sử tin nhắn gần đây từ SQL DB (messageService)
     * và nạp chúng vào ChatMemory của LangChain4j.
     */
    private void hydrateChatMemoryFromDB(ChatMemory chatMemory, Long sessionId) {
        try {
            // Lấy 20 tin nhắn gần nhất từ SQL
            List<ChatMessage> recentDbMessages = messageService.getRecentMessages(sessionId, 20);

            if (recentDbMessages.isEmpty()) {
                return;
            }
            
            // Chuyển đổi từ model DB (ChatMessage) sang model của LangChain4j 
            // (dev.langchain4j.data.message.ChatMessage)
            // Lưu ý: Chúng ta phải thêm chúng theo đúng thứ tự (cũ đến mới)
            for (ChatMessage dbMsg : recentDbMessages) {
                if ("user".equalsIgnoreCase(dbMsg.getSender())) {
                    chatMemory.add(UserMessage.from(dbMsg.getContent()));
                } else if ("assistant".equalsIgnoreCase(dbMsg.getSender())) {
                    chatMemory.add(AiMessage.from(dbMsg.getContent()));
                }
                // (Chúng ta có thể bỏ qua các tin nhắn "system" trong lịch sử DB 
                // vì system prompt được xây dựng riêng)
            }
        } catch (Exception e) {
            log.warn("Failed to hydrate chat memory from DB for session {}: {}", sessionId, e.getMessage());
        }
    }

    // ✅ PHƯƠNG THỨC MỚI ĐỂ BUILD PROMPT VỚI RAG
 // Trong file: demo-2/src/main/java/com/example/demo/service/chat/ChatAIService.java

    /**
     * ✅ THAY ĐỔI SIGNATURE: Thêm ngữ cảnh dài hạn (longTerm) và sở thích (prefs)
     */
    private List<dev.langchain4j.data.message.ChatMessage> buildFinalLc4jMessages(
            List<dev.langchain4j.data.message.ChatMessage> history, 
            String ragContext, 
            String longTermContext, 
            Map<String, Object> userPrefsMap, // ✅ THAY ĐỔI: Chấp nhận Map
            String currentQuery) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý AI hữu ích.\n");

        // ✅ THAY ĐỔI: Logic để xử lý Map
        if (userPrefsMap != null && !userPrefsMap.isEmpty()) {
            sb.append("\n--- SỞ THÍCH CỦA NGƯỜI DÙNG ---\n");
            userPrefsMap.forEach((key, value) -> {
                sb.append(String.format("%s: %s\n", key, value != null ? value.toString() : "N/A"));
            });
        }

        if (longTermContext != null && !longTermContext.isBlank()) {
            sb.append("\n--- BỘ NHỚ DÀI HẠN (TÓM TẮT CÁC PHIÊN TRƯỚC) ---\n");
            sb.append(longTermContext).append("\n");
        }

        sb.append("\n--- BỐI CẢNH NGẮN HẠN (TỪ RAG) ---\n");
        sb.append(ragContext.isEmpty() ? "Không có" : ragContext).append("\n");
        sb.append("\n--- HẾT BỐI CẢNH ---\n\nHãy trả lời câu hỏi hiện tại.");

        messages.add(SystemMessage.from(sb.toString()));

        messages.addAll(history); 
        messages.add(UserMessage.from(currentQuery));

        return messages;
    }


    /**
     * ✅ PHƯƠNG THỨC MỚI:
     * Chạy các phân tích ngữ cảnh nền (bất đồng bộ) để không chặn phản hồi.
     */
    @Async 
    protected void runContextAnalysisAsync(ChatSession session, User user, String prompt) {
        try {
            // 1. Phân tích cảm xúc (Sửa lỗi Constructor)
            EmotionContext emotionContext = emotionContextRepository.findByChatSession_Id(session.getId())
                    .orElseGet(() -> {
                        EmotionContext ctx = new EmotionContext(); // Tạo trống
                        ctx.setChatSession(session); // Set thủ công
                        ctx.setUser(user);         // Set thủ công
                        return ctx;
                    }); // ✅ ĐÃ SỬA
            emotionAnalysisService.analyzeEmotion(prompt, emotionContext);
            emotionContextRepository.save(emotionContext);

            // 2. Cập nhật trạng thái hội thoại
            ConversationState state = conversationStateService.getOrCreateState(session.getId());
            // ⛔ XÓA DÒNG LỖI: conversationStateService.updateConversationState(state, prompt); 
            // (Phương thức này không tồn tại trong service của bạn)
            conversationStateRepository.save(state);

            log.debug("Đã cập nhật Context (Emotion, State) bất đồng bộ cho session {}", session.getId());
        } catch (Exception e) {
            log.warn("Lỗi cập nhật context bất đồng bộ: {}", e.getMessage());
        }
    }
    
    // 🔥 THÊM PHƯƠNG THỨC XỬ LÝ CÂU HỎI MEMORY
    private String handleMemoryQuestion(ChatMemory chatMemory, String currentPrompt) {
        List<dev.langchain4j.data.message.ChatMessage> messages = chatMemory.messages();
        
        if (messages.isEmpty()) {
            return "Chúng ta chưa có cuộc trò chuyện nào trước đó.";
        }
        
        // Lọc chỉ lấy tin nhắn user (bỏ qua system messages và AI responses)
        List<String> userMessages = messages.stream()
            .filter(msg -> msg instanceof UserMessage)
            .map(dev.langchain4j.data.message.ChatMessage::text)
            .filter(msg -> !msg.equals(currentPrompt)) // Bỏ qua câu hỏi hiện tại
            .collect(Collectors.toList());
        
        if (userMessages.isEmpty()) {
            return "Tôi chưa nhận được tin nhắn nào từ bạn trước đây.";
        }
        
        // Lấy tin nhắn user gần nhất
        String lastUserMessage = userMessages.get(userMessages.size() - 1);
        
        // Trả về câu trả lời thông minh hơn
        return "Bạn vừa nhắn: \"" + lastUserMessage + "\". " +
               "Bạn muốn tôi giải thích thêm hay có câu hỏi gì về điều này không?";
    }

    // 🔥 CẬP NHẬT PHƯƠNG THỨC NHẬN DIỆN CÂU HỎI MEMORY
    private boolean isMemoryRelatedQuestion(String prompt) {
        String lowerPrompt = prompt.toLowerCase();
        return lowerPrompt.contains("vừa nhắn") || 
               lowerPrompt.contains("vừa nói") ||
               lowerPrompt.contains("trước đó") ||
               lowerPrompt.contains("nhắc lại") ||
               lowerPrompt.contains("nói gì") ||
               lowerPrompt.matches(".*tôi.*vừa.*nói.*gì.*") ||
               lowerPrompt.matches(".*tôi.*vừa.*nhắn.*gì.*") ||
               lowerPrompt.contains("what did i say") ||
               lowerPrompt.contains("what was my last message");
    }

    private List<Map<String, String>> convertToPromptFormat(List<dev.langchain4j.data.message.ChatMessage> messages) {
        List<Map<String, String>> prompt = new ArrayList<>();
        
        // Thêm system message đầu tiên
        prompt.add(Map.of(
            "role", "system",
            "content", "Bạn là trợ lý AI thông minh. Hãy trả lời tự nhiên và hữu ích."
        ));
        
        // Thêm toàn bộ lịch sử hội thoại
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
String role = message instanceof UserMessage ? "user" : 
                         message instanceof AiMessage ? "assistant" : "system";
            
            prompt.add(Map.of(
                "role", role,
                "content", message.text()
            ));
        }
        
        return prompt;
    }
    

    
    private String buildSystemPrompt(ChatSession session, User user) {
        // Giữ lại logic system prompt hiện tại nhưng đơn giản hóa
        return "Bạn là trợ lý AI thông minh. Hãy trả lời tự nhiên và hữu ích.";
    }
    
    private ChatMessage convertToChatMessage(dev.langchain4j.data.message.ChatMessage lcMessage, ChatSession session) {
        ChatMessage message = new ChatMessage();
        message.setChatSession(session);
        
        if (lcMessage instanceof UserMessage) {
            message.setSender("user");
            message.setContent(lcMessage.text());
        } else if (lcMessage instanceof AiMessage) {
            message.setSender("assistant");
            message.setContent(lcMessage.text());
        } else if (lcMessage instanceof SystemMessage) {
            message.setSender("system");
            message.setContent(lcMessage.text());
        }
        
        return message;
    }
    
    /**
     * Định nghĩa các loại Intent (ý định) của truy vấn.
     */
    private enum QueryIntent {
        RAG_QUERY,      // Câu hỏi cần tìm kiếm ngữ cảnh
        CHITCHAT,       // Chào hỏi xã giao
        MEMORY_QUERY    // ✅ THÊM TRẠNG THÁI MỚI: Câu hỏi về bộ nhớ
    }

    /**
     * (Query Router) Sử dụng LLM để phân loại intent của người dùng.
     * Trả về 'RAG_QUERY' nếu câu hỏi cần tìm kiếm trong bộ nhớ vector,
     * ngược lại trả về 'CHITCHAT'.
     */
    private QueryIntent classifyQueryIntent(String query) {
        // 1. ✅ PRE-FILTER: Lọc nhanh câu hỏi bộ nhớ trước khi gọi LLM (Tiết kiệm chi phí)
        if (isMemoryRelatedQuestion(query)) {
            return QueryIntent.MEMORY_QUERY;
        }

        try {
            // 2. ✅ NÂNG CẤP PROMPT: Thêm MEMORY_QUERY vào prompt của LLM (cho các trường hợp phức tạp hơn)
            String systemPrompt = "Bạn là một AI phân loại truy vấn. " +
                "Nhiệm vụ của bạn là đọc truy vấn và quyết định nó thuộc loại nào trong ba loại sau:\n" +
                "1. RAG_QUERY: Nếu người dùng đang hỏi một câu hỏi cụ thể, yêu cầu thông tin, tóm tắt, phân tích, hoặc hỏi về các sự kiện trong quá khứ (ví dụ: 'giải thích X', 'tại sao Y').\n" +
                "2. CHITCHAT: Nếu người dùng chỉ đang chào hỏi, cảm ơn, hoặc nói câu xã giao ngắn (ví dụ: 'hi', 'cảm ơn bạn', 'tuyệt vời').\n" +
                "3. MEMORY_QUERY: Nếu câu hỏi chính chỉ là hỏi về nội dung cuộc trò chuyện vừa diễn ra (ví dụ: 'bạn đã nói gì', 'tin nhắn cuối của tôi là gì').\n\n" +
                "Chỉ trả về MỘT TỪ: RAG_QUERY, CHITCHAT, hoặc MEMORY_QUERY.";
            
            String response = chatLanguageModel.generate(systemPrompt + "\n\nTruy vấn: " + query);

            if (response.contains("MEMORY_QUERY")) {
                return QueryIntent.MEMORY_QUERY;
            } else if (response.contains("RAG_QUERY")) {
                return QueryIntent.RAG_QUERY;
            } else {
                return QueryIntent.CHITCHAT;
            }
        } catch (Exception e) {
            log.warn("Query intent classification failed: {}. Falling back to RAG_QUERY.", e.getMessage());
            return QueryIntent.RAG_QUERY;
        }
    }
    
 // ✅ THÊM CÁC PHƯƠNG THỨC NÀY VÀO CUỐI CLASS CHATAISERVICE

 // (Cache để kiểm tra query kỹ thuật)
 private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
     .maximumSize(1000)
     .expireAfterWrite(1, TimeUnit.HOURS)
     .build();

 private boolean isTechnicalQuery(String query) {
     if (query == null || query.isBlank()) {
         return false;
     }
     return technicalQueryCache.get(query, this::analyzeTechnicalQuery);
 }

 private boolean analyzeTechnicalQuery(String query) {
     String lowerQuery = query.toLowerCase();
     // (Đây là logic phân tích kỹ thuật của bạn, bạn có thể copy lại
     // logic đầy đủ mà bạn đã viết trước đây)
     String[] technicalKeywords = {"java", "code", "api", "error", "exception", "debug", "sql"};
     for (String keyword : technicalKeywords) {
         if (lowerQuery.contains(keyword)) {
             return true;
         }
     }
     return false;
 }
 
//    private boolean isComplexQuery(String query) {
//        // Query có nhiều từ hoặc chứa từ khóa phức tạp
//        return query.split("\\s+").length > 5 || 
//               query.contains("?") || 
//               query.contains("how") || 
//               query.contains("why");
//    }

//    private Map<String, Double> getHybridWeightsBasedOnQueryType(String query) {
//if (query.contains("recent") || query.contains("mới nhất")) {
//            return Map.of("semantic", 0.4, "recency", 0.4, "keyword", 0.2);
//        } else if (isTechnicalQuery(query)) {
//            return Map.of("semantic", 0.6, "recency", 0.2, "keyword", 0.2);
//        } else {
//            return Map.of("semantic", 0.5, "recency", 0.3, "keyword", 0.2);
//        }
//    }
    
 // Thêm cache cho technical query detection
//    private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
//        .maximumSize(1000)
//        .expireAfterWrite(1, TimeUnit.HOURS)
//        .build();

//    private boolean isTechnicalQuery(String query) {
//        if (query == null || query.isBlank()) {
//            return false;
//        }
//        
//        // Kiểm tra cache trước
//        return technicalQueryCache.get(query, this::analyzeTechnicalQuery);
//    }
    
//    private boolean analyzeTechnicalQuery(String query) {
//        if (query == null || query.isBlank()) {
//            return false;
//        }
//        
//        String lowerQuery = query.toLowerCase();
//        
//        // Danh sách từ khóa kỹ thuật phổ biến (tiếng Anh và tiếng Việt)
//        String[] technicalKeywords = {
//            // Programming concepts
//            "java", "python", "javascript", "c++", "c#", "php", "ruby", "go", "rust", "swift",
//            "code", "programming", "lập trình", "mã", "source code", 
//            "function", "hàm", "method", "phương thức", "class", "lớp", "object", "đối tượng",
//            "variable", "biến", "constant", "hằng số", "loop", "vòng lặp", "condition", "điều kiện",
//            "algorithm", "thuật toán", "data structure", "cấu trúc dữ liệu",
//            "api", "rest", "graphql", "endpoint", "microservice", "microservices",
//            "database", "cơ sở dữ liệu", "sql", "nosql", "mysql", "postgresql", "mongodb",
//            "redis", "elasticsearch", "orm", "hibernate", "jpa",
//            
//            // Error and debugging
//            "error", "lỗi", "exception", "ngoại lệ", "bug", "debug", "gỡ lỗi", "stack trace",
//            "compile", "biên dịch", "runtime", "thời gian chạy",
//            
//            // Technical terms
//            "framework", "thư viện", "library", "dependency", "phụ thuộc",
//            "git", "github", "gitlab", "version control", "kiểm soát phiên bản",
//            "docker", "container", "kubernetes", "k8s", "deployment", "triển khai",
//            "server", "máy chủ", "client", "máy khách", "http", "https", "protocol", "giao thức",
//            "security", "bảo mật", "authentication", "xác thực", "authorization", "ủy quyền",
//            "oauth", "jwt", "token",
//            
//            // Development tools and processes
//            "ide", "intellij", "eclipse", "vscode", "visual studio",
//            "agile", "scrum", "kanban", "ci/cd", "continuous integration", "tích hợp liên tục",
//"test", "kiểm thử", "unit test", "integration test", "kiểm thử tích hợp",
//            "refactor", "tái cấu trúc", "optimize", "tối ưu hóa", "performance", "hiệu năng",
//            
//            // Web technologies
//            "html", "css", "bootstrap", "tailwind", "react", "angular", "vue", "node.js",
//            "spring", "spring boot", "django", "flask", "laravel",
//            
//            // System design
//            "architecture", "kiến trúc", "design pattern", "mẫu thiết kế",
//            "singleton", "factory", "observer", "strategy", "decorator",
//            "scalability", "khả năng mở rộng", "reliability", "độ tin cậy",
//            "load balancing", "cân bằng tải", "caching", "bộ nhớ đệm"
//        };
//        
//        // Kiểm tra từ khóa kỹ thuật
//        for (String keyword : technicalKeywords) {
//            if (lowerQuery.contains(keyword)) {
//                log.debug("Query được nhận diện là technical: '{}' chứa keyword '{}'", query, keyword);
//                return true;
//            }
//        }
//        
//        // Kiểm tra các pattern đặc biệt của query kỹ thuật
//        if (containsTechnicalPatterns(lowerQuery)) {
//            log.debug("Query được nhận diện là technical theo pattern: '{}'", query);
//            return true;
//        }
//        
//        return false;
//    }

//    private boolean containsTechnicalPatterns(String query) {
//        // Pattern 1: Câu hỏi về lỗi hoặc exception
//        if (query.matches(".*(error|exception|lỗi|bug).*(in|trong|with|với).*") ||
//            query.matches(".*how to fix.*(error|exception|lỗi|bug).*") ||
//            query.matches(".*cách sửa.*(lỗi|bug).*")) {
//            return true;
//        }
//        
//        // Pattern 2: Câu hỏi về cú pháp hoặc implementation
//        if (query.matches(".*how to (implement|use|create).*") ||
//            query.matches(".*cách (triển khai|sử dụng|tạo).*") ||
//            query.matches(".*syntax for.*") ||
//            query.matches(".*cú pháp.*")) {
//            return true;
//        }
//        
//        // Pattern 3: Câu hỏi về best practice hoặc optimization
//        if (query.matches(".*best practice.*") ||
//            query.matches(".*best way to.*") ||
//            query.matches(".*cách tốt nhất.*") ||
//            query.matches(".*how to optimize.*") ||
//            query.matches(".*cách tối ưu.*")) {
//            return true;
//        }
//        
//        // Pattern 4: Câu hỏi về version hoặc compatibility
//        if (query.matches(".*version.*compatibility.*") ||
//            query.matches(".*tương thích.*phiên bản.*") ||
//            query.matches(".*which version.*") ||
//            query.matches(".*phiên bản nào.*")) {
//            return true;
//        }
//        
//        return false;
//    }
    
    // ✅ TOPIC DETECTION FOR QUERY
//    private String detectTopicForQuery(String query) {
//        if (query == null || query.length() < 5) return null;
//        
//        try {
//// Simple keyword matching first
//            String lowerQuery = query.toLowerCase();
//            
//            if (lowerQuery.contains("java") || lowerQuery.contains("code") || 
//                lowerQuery.contains("program") || lowerQuery.contains("lập trình")) {
//                return "programming";
//            }
//            
//            if (lowerQuery.contains("weather") || lowerQuery.contains("thời tiết") || 
//                lowerQuery.contains("nhiệt độ")) {
//                return "weather";
//            }
//            
//            if (lowerQuery.contains("music") || lowerQuery.contains("nhạc") || 
//                lowerQuery.contains("bài hát")) {
//                return "music";
//            }
//            
//            // For more complex queries, use AI detection
//            if (query.length() > 20) {
//                return detectTopicWithAI(query);
//            }
//            
//            return null;
//        } catch (Exception e) {
//            log.warn("Query topic detection failed: {}", e.getMessage());
//            return null;
//        }
//    }
    
 // ✅ THÊM PHƯƠNG THỨC detectTopicWithAI VÀO ChatAIService
//    private String detectTopicWithAI(String content) {
//        try {
//            String systemPrompt = "Phân tích đoạn văn và trả về 1 từ khóa chủ đề duy nhất. " +
//                    "Chỉ trả về từ khóa, không giải thích. " +
//                    "Các chủ đề phổ biến: programming, weather, music, sports, food, general.";
//            
//            String userPrompt = "Xác định chủ đề cho đoạn văn sau: " + 
//                    content.substring(0, Math.min(200, content.length()));
//            
//            String fullPrompt = systemPrompt + "\n\n" + userPrompt;
//            String topic = chatLanguageModel.generate(fullPrompt);
//            
//            return topic != null ? topic.trim().toLowerCase() : "general";
//        } catch (Exception e) {
//            log.warn("AI topic detection failed, using fallback: {}", e.getMessage());
//            return "general";
//        }
//    }
    
    // ✅ CHECK IF QUERY IS FROM USER PERSPECTIVE
//    private boolean isUserQuery(String query) {
//        if (query == null) return false;
//        String lowerQuery = query.toLowerCase();
//        
//        return lowerQuery.contains("tôi") || lowerQuery.contains("mình") || 
//               lowerQuery.contains("tớ") || lowerQuery.contains("tao") ||
//               lowerQuery.startsWith("how do i") || lowerQuery.startsWith("how can i");
//    }
//    
//    private SearchStrategy classifyQuery(String query) {
//        if (query == null || query.isBlank()) return SearchStrategy.HYBRID;
//        
//        String lowerQuery = query.toLowerCase();
//        
//        // Query ngắn và có từ khóa kỹ thuật → ưu tiên keyword
//        if (query.length() < 20 && containsTechnicalKeywords(lowerQuery)) {
//            return SearchStrategy.KEYWORD;
//        }
//// Query dài và phức tạp → ưu tiên semantic
//        if (query.length() > 50 || isComplexNaturalLanguage(lowerQuery)) {
//            return SearchStrategy.SEMANTIC;
//        }
//        
//        // Mặc định dùng hybrid
//        return SearchStrategy.HYBRID;
//    }
//    
//    private boolean containsTechnicalKeywords(String query) {
//        // Danh sách từ khóa kỹ thuật phổ biến
//        String[] techKeywords = {"exception", "error", "function", "method", "class", 
//                               "interface", "api", "syntax", "compile", "runtime"};
//        
//        for (String keyword : techKeywords) {
//            if (query.contains(keyword)) return true;
//        }
//        return false;
//    }
//    
//    private boolean isComplexNaturalLanguage(String query) {
//        // Query có nhiều từ và cấu trúc phức tạp
//        String[] words = query.split("\\s+");
//        return words.length > 8 && query.contains("?") && 
//               (query.contains("how") || query.contains("why") || query.contains("what"));
//    }
//    
//    private enum SearchStrategy {
//        KEYWORD, SEMANTIC, HYBRID
//    }
//    
//    private void logRetrievalPerformance(Long sessionId, String prompt, 
//            List<ChatMessage> retrievedMessages, 
//            long durationMs, String method) {
//
//log.info("📊 Retrieval - Session: {}, Method: {}, Messages: {}, Time: {}ms",
//sessionId, method, retrievedMessages.size(), durationMs);
//
//if (!retrievedMessages.isEmpty()) {
//log.debug("Retrieved messages for prompt: '{}'", prompt);
//for (ChatMessage msg : retrievedMessages) {
//log.debug("  - {}: {}", msg.getSender(), msg.getContent());
//}
//}
//}
//    
//    private boolean isExplicitRecallQuestion(String prompt) {
//        if (prompt == null) return false;
//        String lower = prompt.toLowerCase();
//        return lower.contains("nhắc lại") || lower.contains("trước đó") || 
//               lower.contains("vừa nói") || lower.contains("vừa hỏi") || 
//               lower.contains("nói lại") || lower.contains("kể lại");
//    }
//
//private List<ChatMessage> removeDuplicates(List<ChatMessage> retrieved, 
//            List<ChatMessage> recent) {
//if (retrieved == null) retrieved = new ArrayList<>();
//if (recent == null) recent = new ArrayList<>();
//
//Set<Long> recentIds = recent.stream()
//.map(ChatMessage::getId)
//.filter(id -> id != null)
//.collect(Collectors.toSet());
//
//return retrieved.stream()
//.filter(msg -> msg != null && msg.getId() != null)
//.filter(msg -> !recentIds.contains(msg.getId()))
//.collect(Collectors.toList());
//}
//
//    private List<ChatMessage> getFallbackMessages(Long sessionId, int limit) {
//        try {
//            return messageService.getRecentMessages(sessionId, limit);
//        } catch (Exception e) {
//            log.warn("Fallback message retrieval failed: {}", e.getMessage());
//            return List.of();
//        }
//    }
//    
//    // ✅ Helper methods for parallel processing
//    private EmotionContext processEmotion(ChatSession session, User user, String prompt) {
//        try {
//            EmotionContext emotionContext = emotionContextRepository.findByChatSession_Id(session.getId())
//                .orElseGet(() -> createNewEmotionContext(session, user));
//            emotionContext = emotionAnalysisService.analyzeEmotion(prompt, emotionContext);
//            
//            // Đảm bảo không có giá trị null
//            if (emotionContext.getCurrentEmotion() == null) {
//                emotionContext.setCurrentEmotion("neutral");
//            }
//            if (emotionContext.getEmotionIntensity() == null) {
//                emotionContext.setEmotionIntensity(0.5);
//            }
//            
//            return emotionContextRepository.save(emotionContext);
//        } catch (Exception e) {
//            log.warn("Emotion analysis failed: {}", e.getMessage());
//            // Return default với giá trị không null
//            EmotionContext defaultContext = new EmotionContext();
//            defaultContext.setCurrentEmotion("neutral");
//            defaultContext.setEmotionIntensity(0.5);
//            return defaultContext;
//        }
//    }
//
//    private ConversationState processConversationState(Long sessionId, String prompt, List<ChatMessage> recentMessages) {
//        try {
//            ConversationState state = conversationStateService.getOrCreateState(sessionId);
//            updateConversationState(state, prompt, recentMessages);
//            
//            // Đảm bảo không có giá trị null
//            if (state.getConversationStage() == null) {
//                state.setConversationStage("main");
//            }
//            if (state.getCurrentTopic() == null) {
//                state.setCurrentTopic("general");
//            }
//            if (state.getFrustrationLevel() == null) {
//                state.setFrustrationLevel(0);
//            }
//            if (state.getSatisfactionScore() == null) {
//                state.setSatisfactionScore(5);
//            }
//return conversationStateRepository.save(state);
//        } catch (Exception e) {
//            log.warn("Conversation state update failed: {}", e.getMessage());
//            // Return default với giá trị không null
//            ConversationState defaultState = new ConversationState();
//            defaultState.setConversationStage("main");

//            defaultState.setCurrentTopic("general");
//            defaultState.setFrustrationLevel(0);
//            defaultState.setSatisfactionScore(5);
//            return defaultState;
//        }
//    }
//
//    // ✅ CÁC PHƯƠNG THỨC TRỢ GIÚP KHÁC GIỮ NGUYÊN...
//
//    private EmotionContext createNewEmotionContext(ChatSession session, User user) {
//        EmotionContext context = new EmotionContext();
//        context.setChatSession(session);
//        context.setUser(user);
//        context.setCurrentEmotion("neutral");
//        context.setEmotionIntensity(0.5);
//        return context;
//    }
//
//    private void updateConversationState(ConversationState state, String prompt, List<ChatMessage> recentMessages) {
//        // Phát hiện stage mới dựa trên content
//        String newStage = detectConversationStage(prompt, recentMessages);
//        String newTopic = detectCurrentTopic(prompt);
//        
//        // Đảm bảo không null
//        String currentStage = state.getConversationStage() != null ? state.getConversationStage() : "main";
//        String currentTopic = state.getCurrentTopic() != null ? state.getCurrentTopic() : "general";
//        
//        if (!newStage.equals(currentStage) || !newTopic.equals(currentTopic)) {
//            if (state.getStateHistory() == null) {
//                state.setStateHistory(new ArrayList<>());
//            }
//            state.getStateHistory().add(currentStage);
//            state.setConversationStage(newStage);
//            state.setCurrentTopic(newTopic);
//            state.setLastStateChange(LocalDateTime.now());
//        }
//
//        // Adjust frustration level based on message characteristics
//        if (isFrustratedMessage(prompt)) {
//            Integer currentFrustration = state.getFrustrationLevel() != null ? state.getFrustrationLevel() : 0;
//            conversationStateService.adjustFrustrationLevel(state.getChatSession().getId(), currentFrustration + 1);
//        }
//    }
//
//    private String detectConversationStage(String prompt, List<ChatMessage> recentMessages) {
//        String lowerPrompt = prompt.toLowerCase();
//        
//        if (recentMessages.isEmpty() || lowerPrompt.contains("xin chào") || lowerPrompt.contains("hello")) {
//            return "greeting";
//        }
//        if (lowerPrompt.contains("cảm ơn") || lowerPrompt.contains("tạm biệt") || lowerPrompt.contains("bye")) {
//            return "closing";
//        }
//        if (lowerPrompt.contains("?") || lowerPrompt.contains("giải thích") || lowerPrompt.contains("tại sao")) {
//            return "question";
//        }
//if (recentMessages.size() > 3 && lowerPrompt.length() > 20) {
//            return "main";
//        }
//        
//        return "main";
//    }
//
//    private String detectCurrentTopic(String prompt) {
//        // Simple topic detection - can be enhanced
//        if (prompt.contains("java") || prompt.contains("code") || prompt.contains("lập trình")) {
//            return "programming";
//        }
//        if (prompt.contains("thời tiết") || prompt.contains("nhiệt độ")) {
//            return "weather";
//        }
//        if (prompt.contains("âm nhạc") || prompt.contains("bài hát")) {
//            return "music";
//        }
//        return "general";

//    }
//
//    private boolean isFrustratedMessage(String prompt) {
//        String lower = prompt.toLowerCase();
//        return lower.contains("!!!") || lower.contains("??") || 
//               lower.contains("không hiểu") || lower.contains("sao vậy") ||
//               lower.length() < 5 && lower.contains("?");
//    }
//
//    // ✅ FIXED METHOD SIGNATURE
//    private List<Map<String, String>> buildEnhancedPrompt(List<ChatMessage> messages, String currentPrompt,
//            ChatSession session, EmotionContext emotionContext, ConversationState state, User user,
//            List<ChatMessage> retrievalMessages) {
//
//        // Get user preferences
//        Map<String, Object> userPreferences = userPreferenceService.getUserPreferencesForPrompt(user.getId());
//
//        // Get hierarchical context
//        int currentSegment = memoryManager.getCurrentSegment(session); // Thêm phương thức này trong MemorySummaryManager
//        String hierarchicalContext = hierarchicalMemoryManager.getHierarchicalContext(session, currentSegment, currentPrompt);
//
//        // Build enhanced system prompt với hierarchical context
//        String systemPrompt = buildSystemPromptWithContext(
//            emotionContext, state, userPreferences, hierarchicalContext
//        );
//
//        List<Map<String, String>> enhancedPrompt = new ArrayList<>();
//        enhancedPrompt.add(Map.of("role", "system", "content", systemPrompt));
//
//        // Thêm retrieval context nếu có
//        if (!retrievalMessages.isEmpty()) {
//            String context = buildRetrievalContext(retrievalMessages);
//            enhancedPrompt.add(Map.of("role", "system", "content", 
//                "THÔNG TIN NGỮ CẢNH TỪ CUỘC TRÒ CHUYỆN TRƯỚC ĐÂY:\n" + context));
//        }
//
//        // Add conversation history
//        for (ChatMessage msg : messages) {
//            enhancedPrompt.add(Map.of(
//                "role", msg.getSender().toLowerCase(),
//                "content", msg.getContent()
//            ));
//        }
//
//        // Add current prompt
//        enhancedPrompt.add(Map.of("role", "user", "content", currentPrompt));
//
//        return enhancedPrompt;
//    }
//    
//    private String buildRetrievalContext(List<ChatMessage> retrievedMessages) {
//        StringBuilder context = new StringBuilder();
//for (ChatMessage msg : retrievedMessages) {
//            context.append(msg.getSender()).append(": ")
//                   .append(msg.getContent()).append("\n");
//        }
//        return context.toString();
//    }
//
//    private String buildSystemPromptWithContext(EmotionContext emotionContext, ConversationState state,
//            Map<String, Object> userPreferences, String hierarchicalContext) {
//
//        StringBuilder prompt = new StringBuilder();
//        prompt.append("Bạn là trợ lý AI thông minh. ");
//
//        // Thêm hierarchical context nếu có
//        if (hierarchicalContext != null && !hierarchicalContext.isEmpty()) {
//            prompt.append("Dưới đây là ngữ cảnh phân cấp từ cuộc trò chuyện:\n")
//                  .append(hierarchicalContext)
//                  .append("\nHãy sử dụng thông tin này để hiểu ngữ cảnh tổng quan.\n\n");
//        }
//
//        // Add emotion context - FIX NULL CHECK
//        if (emotionContext != null && emotionContext.getCurrentEmotion() != null) {
//            Double intensity = emotionContext.getEmotionIntensity();
//            double safeIntensity = intensity != null ? intensity : 0.5; // Default value
//            
//            prompt.append(String.format("Người dùng đang có cảm xúc %s (cường độ %.1f). ",
//                emotionContext.getCurrentEmotion(), safeIntensity));
//
//            if (safeIntensity > 0.7) {
//                prompt.append("Hãy phản hồi với sự đồng cảm. ");
//            }
//        }
//
//        // Add conversation state - FIX NULL CHECK
//        if (state != null) {
//            String stage = state.getConversationStage() != null ? state.getConversationStage() : "unknown";
//            String topic = state.getCurrentTopic() != null ? state.getCurrentTopic() : "general";
//            
//            prompt.append(String.format("Cuộc trò chuyện đang ở giai đoạn %s, chủ đề %s. ",
//                stage, topic));
//
//            Integer frustrationLevel = state.getFrustrationLevel();
//            if (frustrationLevel != null && frustrationLevel > 5) {
//                prompt.append("Người dùng đang hơi khó chịu, hãy trả lời cẩn thận và rõ ràng. ");
//            }
//        }
//
//        // Add user preferences từ Map - FIX NULL CHECK
//        if (userPreferences != null && !userPreferences.isEmpty()) {
//            String style = userPreferences.get("communicationStyle") != null ? 
//                userPreferences.get("communicationStyle").toString() : "neutral";
//            String detail = userPreferences.get("detailPreference") != null ? 
//                userPreferences.get("detailPreference").toString() : "medium";
//            
//            prompt.append(String.format("Người dùng thích phong cách %s, mức độ chi tiết %s. ",
//                style, detail));
//        }
//
//        prompt.append("Hãy trả lời tự nhiên và hữu ích.");
//        return prompt.toString();
//    }
//public List<Map<String, String>> buildPromptWithHierarchicalMemory(
//            List<ChatMessage> messages, String currentPrompt, 
//            ChatSession session, int currentSegment) {
//        
//        String hierarchicalContext = hierarchicalMemoryManager
//                .getHierarchicalContext(session, currentSegment, currentPrompt);
//        
//        List<Map<String, String>> prompt = new ArrayList<>();
//        
//        prompt.add(Map.of("role", "system", "content", 
//            "Bạn là trợ lý AI thông minh. Dưới đây là ngữ cảnh phân cấp từ cuộc trò chuyện:\n" +
//            hierarchicalContext +
//            "\nHãy sử dụng thông tin này để hiểu ngữ cảnh tổng quan."));
//        
//        // Thêm conversation history
//        for (ChatMessage msg : messages) {
//            prompt.add(Map.of(
//                "role", msg.getSender().toLowerCase(),
//                "content", msg.getContent()
//            ));
//        }
//        
//        prompt.add(Map.of("role", "user", "content", currentPrompt));
//        
//        return prompt;
//    }
//
//    private void updateSatisfactionScore(Long sessionId, String userPrompt, String aiResponse) {
//        // Simple satisfaction scoring based on response quality
//        int scoreChange = 0;
//        
//        if (aiResponse == null || aiResponse.length() < 20) {
//            scoreChange = -1; // Response too short
//        } else if (aiResponse.length() > 300) {
//            scoreChange = 1; // Detailed response
//        }
//        
//        if (aiResponse != null && (aiResponse.contains("xin lỗi") || aiResponse.contains("không hiểu"))) {
//            scoreChange = -2; // AI confused
//        }
//        
//        if (scoreChange != 0) {
//            ConversationState state = conversationStateService.getOrCreateState(sessionId);
//            Integer currentScore = state.getSatisfactionScore() != null ? state.getSatisfactionScore() : 5;
//            int newScore = Math.min(Math.max(currentScore + scoreChange, 1), 10);
//            state.setSatisfactionScore(newScore);
//            conversationStateRepository.save(state);
//        }
//    }
//
//    // Hàm nhận diện câu hỏi dạng nhắc lại/thông tin cũ
//    private boolean isRecallQuestion(String prompt) {
//        if (prompt == null) return false;
//        String lower = prompt.toLowerCase();
//        return lower.contains("nhắc lại") || lower.contains("trước đó") || 
//               lower.contains("vừa nói") || lower.contains("vừa hỏi") || 
//               lower.contains("trả lời lại") || lower.contains("nội dung cũ") || 
//               lower.contains("thông tin cũ");
//    }
//
//    // Hàm nhận diện intent tham chiếu ngữ cảnh
//    private boolean isReferenceIntent(String prompt) {
//        if (prompt == null) return false;
//        String lower = prompt.toLowerCase();
//        return lower.contains("tại sao") || lower.contains("ý bạn") || 
//               lower.contains("cái đó") || lower.contains("đó là gì") ||
//lower.contains("vì sao") || lower.contains("liên quan") || 
//               lower.contains("vừa nói") || lower.contains("trước đó");
//    }
}