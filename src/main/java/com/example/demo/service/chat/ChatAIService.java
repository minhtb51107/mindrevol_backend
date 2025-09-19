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
// import com.example.demo.model.chat.MemorySummary; // 🔥 ĐÃ XÓA
import com.example.demo.model.chat.ConversationState;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
import com.example.demo.repository.chat.UserPreferenceRepository.UserPreferenceRepository;
// import com.example.demo.repository.chat.memory.MemorySummaryRepo; // 🔥 ĐÃ XÓA
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
// import com.example.demo.service.chat.memory.HierarchicalMemoryManager; // 🔥 ĐÃ XÓA
// import com.example.demo.service.chat.memory.MemorySummaryManager; // 🔥 ĐÃ XÓA
// import com.example.demo.service.chat.memory.PromptBuilder; // 🔥 ĐÃ XÓA
// import com.example.demo.service.chat.memory.RedisChatMemoryService; // 🔥 ĐÃ XÓA
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
    // private final RedisChatMemoryService redisChatMemoryService; // 🔥 ĐÃ XÓA
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageService messageService;
    // private final MemorySummaryManager memoryManager; // 🔥 ĐÃ XÓA
    // private final PromptBuilder promptBuilder; // 🔥 ĐÃ XÓA
    //private final OpenAIService openAIService;
    //private final SpringAIChatService springAIChatService;
    //private final SpringAIEmbeddingService embeddingService;
    //private final EmbeddingService embeddingService;
    //private final VectorStoreService vectorStoreService;
    
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
    
    // private final HierarchicalMemoryManager hierarchicalMemoryManager; // 🔥 ĐÃ XÓA
    
    // private final MemorySummaryRepo summaryRepo; // 🔥 ĐÃ XÓA
    
    private final LangChainChatMemoryService langChainChatMemoryService; // ✅ ĐÃ HỢP NHẤT
    private final ConversationSummaryService conversationSummaryService;
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    
    private final RerankingService rerankingService; 
    // private final LangChainChatMemoryService langChain4jMemoryService; // 🔥 ĐÃ XÓA (Bị trùng)


 // TRONG ChatAIService.java
    public String processMessages(Long sessionId, List<ChatMessageDTO> messageDTOs, User user) {
        try {
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

            String prompt = messageDTOs.get(messageDTOs.size() - 1).getContent();
            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(sessionId);

            if (chatMemory.messages().isEmpty()) {
                 log.debug("Chat memory for session {} is empty. Hydrating from database...", sessionId);
                 hydrateChatMemoryFromDB(chatMemory, sessionId);
            }

            // === 🔥 LOGIC BỘ NHỚ DÀI HẠN ĐÃ ĐƯỢC ĐƠN GIẢN HÓA ===
            // Biến này không cần thiết nữa vì chatMemory.messages() đã chứa tóm tắt
            // String longTermContext = conversationSummaryService.getSummaryForPrompt(session); // 🔥 ĐÃ XÓA
            
            Map<String, Object> userPrefsMap = userPreferenceService.getUserPreferencesForPrompt(user.getId());
            runContextAnalysisAsync(session, user, prompt);

            
            // === BẮT ĐẦU PIPELINE ĐIỀU PHỐI (ORCHESTRATION) ===

            String ragContext = ""; 
            String reply;
            
            QueryIntent intent = classifyQueryIntent(prompt);
            log.debug("Query intent classified as: {}", intent);

            if (intent == QueryIntent.CHITCHAT) {
                // Luồng 1: CHITCHAT
                log.debug("Handling as CHITCHAT. Skipping RAG.");
                
                 // ✅ SỬA ĐỔI: Chỉ cần truyền chatMemory.messages()
                 List<dev.langchain4j.data.message.ChatMessage> lcMessages = 
                        buildFinalLc4jMessages(chatMemory.messages(), ragContext, userPrefsMap, prompt);
                
                 // Gọi AI
                chatMemory.add(UserMessage.from(prompt));
                Response<AiMessage> response = chatLanguageModel.generate(lcMessages); 
                reply = response.content().text();
                chatMemory.add(AiMessage.from(reply));

            } else if (intent == QueryIntent.MEMORY_QUERY) {
                // Luồng 2: MEMORY_QUERY
                log.debug("Handling as MEMORY_QUERY. Using direct memory handler.");
                reply = handleMemoryQuestion(chatMemory, prompt);
                
                chatMemory.add(UserMessage.from(prompt));
                chatMemory.add(AiMessage.from(reply));
                
            } else {
                // Luồng 3: RAG_QUERY
                log.debug("Handling as RAG_QUERY. Running full RAG pipeline.");
                
                // ... (Logic RAG giữ nguyên) ...
                Embedding queryEmbedding = embeddingModel.embed(prompt).content();
                Filter sessionMessageFilter = new IsEqualTo("sessionId", session.getId().toString());
                Filter userKnowledgeFilter = new And(
                    new IsEqualTo("userId", user.getId().toString()),
                    new IsEqualTo("docType", "knowledge") 
                );
                Filter finalFilter = new Or(sessionMessageFilter, userKnowledgeFilter);

                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(20) 
                        .filter(finalFilter)
                        .build();

                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
                List<EmbeddingMatch<TextSegment>> initialMatches = searchResult.matches();

                List<EmbeddingMatch<TextSegment>> rerankedMatches;
                if (isTechnicalQuery(prompt)) {
                    log.debug("Technical query detected. Using local HYBRID rerank.");
                    Map<String, Double> weights = Map.of("semantic", 0.4, "recency", 0.3, "keyword", 0.3);
                    rerankedMatches = rerankingService.hybridRerank(prompt, initialMatches, weights, 5);
                } else {
                    log.debug("Semantic query detected. Using COHERE rerank.");
                    rerankedMatches = rerankingService.rerankResults(prompt, initialMatches, 5);
                }

                ragContext = rerankedMatches.stream()
                        .map(match -> match.embedded().text())
                        .collect(Collectors.joining("\n---\n"));

                // ✅ SỬA ĐỔI: Chỉ cần truyền chatMemory.messages()
                List<dev.langchain4j.data.message.ChatMessage> lcMessages = 
                        buildFinalLc4jMessages(chatMemory.messages(), ragContext, userPrefsMap, prompt);
                
                // Gọi AI
                chatMemory.add(UserMessage.from(prompt));
                Response<AiMessage> response = chatLanguageModel.generate(lcMessages); 
                reply = response.content().text();
                chatMemory.add(AiMessage.from(reply));
            }

            // === LƯU TRỮ (LUÔN CHẠY) ===
            ChatMessage userMsgDb = messageService.saveMessage(session, "user", prompt);
            ChatMessage aiMsgDb = messageService.saveMessage(session, "assistant", reply);
            saveMessagesToVectorStore(userMsgDb, aiMsgDb, session); 
            
            return reply;

        } catch (Exception e) {
            log.error("Lỗi xử lý processMessages: {}", e.getMessage(), e);
            return fallbackService.getEmergencyResponse();
        }
    }
 
    // ... (Các phương thức khác giữ nguyên) ...
    
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

 // Trong file: demo-2/src/main/java/com/example/demo/service/chat/ChatAIService.java

    /**
     * ✅ THAY ĐỔI SIGNATURE: Loại bỏ 'longTermContext'
     */
    private List<dev.langchain4j.data.message.ChatMessage> buildFinalLc4jMessages(
            List<dev.langchain4j.data.message.ChatMessage> history, // history này đã chứa tóm tắt
            String ragContext, 
            Map<String, Object> userPrefsMap, 
            String currentQuery) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý AI hữu ích.\n");

        if (userPrefsMap != null && !userPrefsMap.isEmpty()) {
            sb.append("\n--- SỞ THÍCH CỦA NGƯỜI DÙNG ---\n");
            userPrefsMap.forEach((key, value) -> {
                sb.append(String.format("%s: %s\n", key, value != null ? value.toString() : "N/A"));
            });
        }

        // 🔥 ĐÃ XÓA KHỐI LOGIC 'longTermContext' VÌ NÓ ĐÃ CÓ TRONG 'history'
        // if (longTermContext != null && !longTermContext.isBlank()) { ... }

        sb.append("\n--- BỐI CẢNH NGẮN HẠN (TỪ RAG) ---\n");
        sb.append(ragContext.isEmpty() ? "Không có" : ragContext).append("\n");
        sb.append("\n--- HẾT BỐI CẢNH ---\n\nHãy trả lời câu hỏi hiện tại.");

        messages.add(SystemMessage.from(sb.toString()));

        // 'history' đã bao gồm (Tóm tắt + tin nhắn gần đây)
        messages.addAll(history); 
        
        // Chỉ thêm câu hỏi hiện tại (nó chưa có trong history)
        // LƯU Ý: Đảm bảo 'history' không bao gồm currentQuery
        // (Trong logic ở trên, chúng ta add(UserMessage) SAU khi build prompt, nên điều này là ĐÚNG)
        
        // messages.add(UserMessage.from(currentQuery)); // BỊ TRÙNG LẶP NẾU history ĐÃ BAO GỒM NÓ
        
        // KIỂM TRA LẠI:
        // 1. chatMemory.messages() được gọi -> trả về [Summary, msg1, msg2]
        // 2. buildFinalLc4jMessages(history, ...) được gọi
        // 3. chatMemory.add(UserMessage.from(prompt)) được gọi
        
        // -> Vì vậy, 'history' chưa chứa 'currentQuery'. Chúng ta cần thêm nó.
        // NHƯNG, trong code cũ của bạn, 'currentQuery' LÀ tin nhắn cuối cùng trong 'history'.
        // Hãy kiểm tra lại `processMessages`:
        
        // 1. chatMemory.add(UserMessage.from(prompt)); // <- Bạn thêm nó VÀO BỘ NHỚ
        // 2. Response<AiMessage> response = chatLanguageModel.generate(lcMessages); // <- Bạn gọi generate
        // 3. chatMemory.add(AiMessage.from(reply)); // <- Bạn thêm phản hồi
        
        // A-ha! Trong code của bạn, `buildFinalLc4jMessages` được gọi TRƯỚC khi `chatMemory.add(UserMessage.from(prompt))`.
        
        // VẬY THÌ: 
        // 1. `history = chatMemory.messages()` (Chưa chứa prompt hiện tại)
        // 2. `lcMessages = buildFinalLc4jMessages(history, ...)`
        // 3. Phương thức `buildFinalLc4jMessages` CẦN thêm `currentQuery`.
        
        // HÃY XEM LẠI buildFinalLc4jMessages gốc của bạn:
        /*
        private List<dev.langchain4j.data.message.ChatMessage> buildFinalLc4jMessages(
            ...
            String currentQuery) {
            ...
            messages.addAll(history); 
            messages.add(UserMessage.from(currentQuery)); // <- NÓ ĐÂY RỒI
            return messages;
        }
        */
       
       // -> Vậy code của tôi ở trên LÀ ĐÚNG. 
       // history không chứa currentQuery, và chúng ta thêm nó vào cuối.

        messages.addAll(history); 
        messages.add(UserMessage.from(currentQuery));

        return messages;
    }
    
    // ... (Phần còn lại của ChatAIService.java giữ nguyên) ...

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
        
        prompt.add(Map.of(
            "role", "system",
            "content", "Bạn là trợ lý AI thông minh. Hãy trả lời tự nhiên và hữu ích."
        ));
        
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
    
    private enum QueryIntent {
        RAG_QUERY,      // Câu hỏi cần tìm kiếm ngữ cảnh
        CHITCHAT,       // Chào hỏi xã giao
        MEMORY_QUERY    // ✅ THÊM TRẠNG THÁI MỚI: Câu hỏi về bộ nhớ
    }

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
 
    // 🔥 CÁC PHƯƠNG THỨC LIÊN QUAN ĐẾN HỆ THỐNG CUSTOM CŨ (ĐÃ BỊ COMMENT OUT) SẼ BỊ XÓA HOÀN TOÀN
    
    // private boolean isComplexQuery(String query) { ... }
    // private Map<String, Double> getHybridWeightsBasedOnQueryType(String query) { ... }
    // private boolean analyzeTechnicalQuery(String query) { ... } // (Đã giữ lại 1 phiên bản)
    // private boolean containsTechnicalPatterns(String query) { ... }
    // private String detectTopicForQuery(String query) { ... }
    // private String detectTopicWithAI(String content) { ... }
    // private boolean isUserQuery(String query) { ... }
    // private SearchStrategy classifyQuery(String query) { ... }
    // private boolean containsTechnicalKeywords(String query) { ... }
    // private boolean isComplexNaturalLanguage(String query) { ... }
    // private enum SearchStrategy { ... }
    // private void logRetrievalPerformance(...) { ... }
    // private boolean isExplicitRecallQuestion(String prompt) { ... }
    // private List<ChatMessage> removeDuplicates(...) { ... }
    // private List<ChatMessage> getFallbackMessages(...) { ... }
    // private EmotionContext processEmotion(...) { ... }
    // private ConversationState processConversationState(...) { ... }
    // private EmotionContext createNewEmotionContext(...) { ... }
    // private void updateConversationState(...) { ... }
    // private String detectConversationStage(...) { ... }
    // private String detectCurrentTopic(...) { ... }
    // private boolean isFrustratedMessage(String prompt) { ... }
    // private List<Map<String, String>> buildEnhancedPrompt(...) { ... } // 🔥 ĐÃ XÓA
    // private String buildRetrievalContext(...) { ... }
    // private String buildSystemPromptWithContext(...) { ... } // 🔥 ĐÃ XÓA
    // public List<Map<String, String>> buildPromptWithHierarchicalMemory(...) { ... } // 🔥 ĐÃ XÓA
    // private void updateSatisfactionScore(...) { ... }
    // private boolean isRecallQuestion(String prompt) { ... }
    // private boolean isReferenceIntent(String prompt) { ... }
}