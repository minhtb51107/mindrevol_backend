//package com.example.demo.service.chat.memory;
//
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.output.Response;
//import dev.langchain4j.data.embedding.Embedding;
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.model.chat.MemorySummary;
//import com.example.demo.model.chat.MemorySummaryLog;
//import com.example.demo.model.chat.ConversationStage;
//import com.example.demo.model.chat.MemorySummaryResult;
//import com.example.demo.repository.chat.memory.MemorySummaryLogRepo;
//import com.example.demo.repository.chat.memory.MemorySummaryRepo;
////import com.example.demo.service.chat.vector.VectorStoreService;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//public class MemorySummaryManager {
//    //private final VectorStoreService vectorStoreService;
//    private final HierarchicalMemoryManager hierarchicalMemoryManager;
//    private final MemorySummaryRepo summaryRepo;
//    private final MemorySummaryLogRepo logRepo;
//    private final MemorySummarizerService summarizer;
//    private final EmbeddingModel embeddingModel; // Thay thế SpringAIEmbeddingService
//    private final ChatLanguageModel chatLanguageModel;
//    
//    /**
//     * Truy xuất tất cả tóm tắt của một phiên chat (theo các segment)
//     */
//    public List<MemorySummary> getAllSummariesForSession(ChatSession session) {
//        return summaryRepo.findAllByChatSessionOrderByTopicSegmentAsc(session);
//    }
//    
//    /**
//     * Tìm kiếm các message liên quan nhất đến câu hỏi hiện tại dựa trên embedding
//     * Trả về danh sách topK message có độ tương đồng cao nhất
//     */
////    public List<ChatMessage> findRelevantMessages(List<ChatMessage> messages, String currentInput, int topK) {
////        if (messages == null || messages.isEmpty() || currentInput == null || currentInput.isBlank()) {
////            return List.of();
////        }
////
////        Long sessionId = messages.get(0).getChatSession().getId();
////
////        // Gọi phương thức của VectorStoreService
////        return vectorStoreService.findSimilarMessages(sessionId, currentInput, topK);
////    }
//
//    
//    /**
//     * Truy xuất tóm tắt dài hạn theo session và segment
//     */
//    public Optional<MemorySummary> getLongTermSummary(ChatSession session, int segment) {
//        return summaryRepo.findByChatSessionAndTopicSegment(session, segment);
//    }
//
//    /**
//     * Truy xuất tóm tắt mới nhất của một phiên chat
//     */
//    public Optional<MemorySummary> getLatestSummary(ChatSession session) {
//        return summaryRepo.findTopByChatSessionOrderByLastUpdatedDesc(session);
//    }
//
//    // Kiểm tra xem có cần cập nhật bộ nhớ không
//    public boolean shouldUpdateMemory(ChatSession session, List<ChatMessage> recentMessages) {
//        Optional<MemorySummary> optional = summaryRepo.findByChatSession(session);
//        // Nếu chưa có summary thì cần tạo mới
//        if (optional.isEmpty()) return true;
//
//        MemorySummary summary = optional.get();
//
//        // Kiểm tra timeout (10 phút)
//        if (isTimeout(summary)) return true;
//
//        // Kiểm tra hội thoại dài
//        if (isConversationTooLong(recentMessages)) return true;
//
//        // Kiểm tra chuyển chủ đề
//        if (shouldIncreaseTopicSegment(recentMessages)) return true;
//
//        return false;
//    }
//
//    // Kiểm tra timeout (10 phút)
//    private boolean isTimeout(MemorySummary summary) {
//        return Duration.between(summary.getLastUpdated(), LocalDateTime.now()).toMinutes() > 10;
//    }
//
//    // Kiểm tra hội thoại dài
//    private boolean isConversationTooLong(List<ChatMessage> messages) {
//        int maxMessages = 20; // ngưỡng số lượng message
//        int maxTotalLength = 3000; // ngưỡng tổng độ dài hội thoại
//        if (messages.size() > maxMessages) return true;
//        int totalLength = messages.stream().mapToInt(m -> m.getContent().length()).sum();
//        if (totalLength > maxTotalLength) return true;
//        ChatMessage latest = messages.get(messages.size() - 1);
//        // Nếu message cuối quá dài (>300 ký tự)
//        if (latest.getContent().length() > 300) return true;
//        return false;
//    }
//
//    // Danh sách từ khóa chủ đề phổ biến (lập trình Java)
//    private static final List<String> DOMAIN_KEYWORDS = List.of(
//        "java", "kiểu dữ liệu", "class", "object", "method", "function", "biến", "lập trình", "array", "list", "map", "string", "int", "float", "double", "boolean"
//    );
//
//    // Nhận diện intent tham chiếu ngữ cảnh
//    private boolean isReferenceIntent(String content) {
//        String lower = content.toLowerCase();
//        return lower.contains("tại sao") || lower.contains("ý bạn") || lower.contains("cái đó") || lower.contains("đó là gì") || lower.contains("vì sao") || lower.contains("liên quan") || lower.contains("vừa nói") || lower.contains("trước đó");
//    }
//
//    // Kiểm tra có nên tăng segment (chuyển chủ đề) không
//    private boolean shouldIncreaseTopicSegment(List<ChatMessage> recent) {
//        if (recent.size() < 2) return false;
//
//        ChatMessage prev = recent.get(recent.size() - 2);
//        ChatMessage latest = recent.get(recent.size() - 1);
//
//        // 1. Kiểm tra intent tham chiếu ngữ cảnh
//        if (isReferenceIntent(latest.getContent())) {
//            log.info("[TopicShift] Intent tham chiếu ngữ cảnh, giữ nguyên chủ đề.");
//            return false;
//        }
//
//        // 2. Kiểm tra từ khóa chủ đề
//        boolean prevHasKeyword = DOMAIN_KEYWORDS.stream().anyMatch(k -> prev.getContent().toLowerCase().contains(k));
//        boolean latestHasKeyword = DOMAIN_KEYWORDS.stream().anyMatch(k -> latest.getContent().toLowerCase().contains(k));
//        if (prevHasKeyword && latestHasKeyword) {
//            log.info("[TopicShift] Cả hai câu đều chứa từ khóa chủ đề, giữ nguyên chủ đề.");
//            return false;
//        }
//
//        // 3. Kiểm tra similarity embedding với ngưỡng tối ưu
//        try {
//            double similarity = cosineSimilarity(
//                getEmbedding(prev.getContent()),
//                getEmbedding(latest.getContent())
//            );
//            log.info("[TopicShift] Similarity giữa hai câu: {} vs {} => {}", prev.getContent(), latest.getContent(), similarity);
//            if (similarity >= 0.65) {
//                log.info("[TopicShift] Similarity cao (>=0.65), giữ nguyên chủ đề.");
//                return false;
//            } else {
//                log.info("[TopicShift] Similarity thấp (<0.65), chuyển chủ đề.");
//                return true;
//            }
//        } catch (Exception e) {
//            log.warn("[TopicShift] Embedding failed. Fallback assume same topic. Reason: {}", e.getMessage());
//            return false; // fallback safe
//        }
//    }
//    
//    // Giới hạn ký tự cho summary
//    private static final int MAX_SUMMARY_CHARS = 4000;
//
//    // Cắt ngắn summary nếu vượt quá giới hạn
//    private String truncateSummary(String content) {
//        if (content.length() <= MAX_SUMMARY_CHARS) return content;
//
//        // Gửi lại nội dung cũ đến AI để tóm gọn thêm
//        String prompt = "Tóm tắt nội dung sau sao cho không vượt quá " + MAX_SUMMARY_CHARS + " ký tự:\n\n" + content;
//
//        try {
//            String shortened = chatLanguageModel.generate(prompt);
//            return shortened.length() > MAX_SUMMARY_CHARS ? shortened.substring(0, MAX_SUMMARY_CHARS) : shortened;
//        } catch (Exception e) {
//            log.warn("Re-summarizing failed, fallback truncate. Reason: {}", e.getMessage());
//            return content.substring(0, MAX_SUMMARY_CHARS);
//        }
//    }
//
//    // Cập nhật summary cho session
//    public void updateSummary(ChatSession session, List<ChatMessage> recent) throws Exception {
//        
//        String reason = "auto: timeout or topic shift";
//        // 1. Tóm tắt đoạn chat
//        MemorySummaryResult result = summarizer.summarize(recent, "auto");
//        String condensed = truncateSummary(result.getContent());
//
//        // 2. Sinh userPersona & conversationGoal
//        String persona = generatePersona(recent);
//        String goal    = generateGoal(recent);
//
//        // 3. Lưu vào entity
//        MemorySummary memory = summaryRepo.findByChatSession(session)
//            .orElse(new MemorySummary());
//
//        memory.setChatSession(session);
//        memory.setSummaryContent(condensed);
//        memory.setLastUpdated(LocalDateTime.now());
//        memory.setTokensUsed(result.getTokensUsed());
//        memory.setUpdateReason(result.getReason());
//        memory.setSummaryType("auto");
//
//        int currentSegment = memory.getTopicSegment();
//        ConversationStage stage = analyze(recent);
//
//        // Tăng segment nếu có chuyển chủ đề
//        if (stage == ConversationStage.TOPIC_SHIFT) {
//            currentSegment++;
//        }
//        memory.setConversationStage(stage.name().toLowerCase());
//        memory.setTopicSegment(currentSegment);
//
//        summaryRepo.save(memory);
//
//        // Lưu log để theo dõi
//        MemorySummaryLog loga = new MemorySummaryLog();
//        loga.setSession(session);
//        loga.setFullPrompt(result.getPromptUsed());
//        loga.setSummaryResult(result.getContent());
//        loga.setCreatedAt(LocalDateTime.now());
//        logRepo.save(loga);
//
//        // Trong phương thức updateSummary, thêm dòng này ở cuối:
//        hierarchicalMemoryManager.checkAndCreateHierarchicalSummary(session, currentSegment);
//        
//        log.info("🧠 Summary updated | segment: {}, stage: {}, reason: {}, detectedStage: {}", 
//                currentSegment, stage.name(), reason, stage);
//    }
//    
//    // Tạo persona người dùng từ lịch sử chat
//    public String generatePersona(List<ChatMessage> history) throws Exception {
//        StringBuilder ctx = new StringBuilder();
//        history.forEach(m -> ctx.append(m.getSender()).append(": ").append(m.getContent()).append("\n"));
//        
//        String prompt = "Bạn là trợ lý giúp xác định tính cách người dùng từ lịch sử trò chuyện.\n\n" +
//                        "Dựa trên đoạn hội thoại sau, tóm tắt thành 1 câu: \"Người dùng là …\":\n\n" + ctx.toString();
//        
//        return chatLanguageModel.generate(prompt).trim();
//    }
//
//    // Tạo mục tiêu hội thoại từ lịch sử chat
//    public String generateGoal(List<ChatMessage> history) throws Exception {
//        StringBuilder ctx = new StringBuilder();
//        history.forEach(m -> ctx.append(m.getSender()).append(": ").append(m.getContent()).append("\n"));
//        
//        String prompt = "Bạn là trợ lý tóm tắt mục tiêu cuộc trò chuyện.\n\n" +
//                        "Dựa vào đoạn hội thoại, người dùng đang hướng tới mục tiêu gì? Trả về 1–2 từ ngắn gọn.\n\n" + ctx.toString();
//        
//        return chatLanguageModel.generate(prompt).trim();
//    }
//    
//    // Phân tích giai đoạn hội thoại
//    public ConversationStage analyze(List<ChatMessage> messages) {
//        StringBuilder history = new StringBuilder();
//        for (ChatMessage msg : messages) {
//            history.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
//        }
//
//        String prompt = "Bạn là một trợ lý AI chuyên phân tích cuộc trò chuyện.\n\n" +
//                        "Dưới đây là đoạn hội thoại. Hãy xác định nó đang ở giai đoạn nào trong các giai đoạn sau:\n\n" +
//                        "INTRO, BRAINSTORMING, SOLUTION, CONCLUSION, REFINEMENT, TOPIC_SHIFT\n\n" +
//                        "Chỉ trả về đúng 1 từ là tên giai đoạn (in hoa, không mô tả thêm).\n\n" + history.toString();
//
//        try {
//            String result = chatLanguageModel.generate(prompt).trim().toUpperCase();
//            
//            return ConversationStage.valueOf(result);
//        } catch (Exception e) {
//            return ConversationStage.UNKNOWN;
//        }
//    }
//    
//    public int getCurrentSegment(ChatSession session) {
//        return summaryRepo.findByChatSession(session)
//            .map(MemorySummary::getTopicSegment)
//            .orElse(0);
//    }
//
//    // ========== CÁC PHƯƠNG THỨC EMBEDDING THAY THẾ ==========
//    
//    public Embedding getEmbedding(String input) {
//        Response<Embedding> response = embeddingModel.embed(input);
//        return response.content();
//    }
//    
//    public double cosineSimilarity(Embedding v1, Embedding v2) {
//        if (v1.vector().length != v2.vector().length) 
//            throw new IllegalArgumentException("Vector size mismatch");
//        
//        float[] vec1 = v1.vector();
//        float[] vec2 = v2.vector();
//        
//        double dot = 0, norm1 = 0, norm2 = 0;
//        for (int i = 0; i < vec1.length; i++) {
//            dot += vec1[i] * vec2[i];
//            norm1 += Math.pow(vec1[i], 2);
//            norm2 += Math.pow(vec2[i], 2);
//        }
//        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
//    }
//    
//    public boolean isSimilar(String a, String b) {
//        try {
//            Embedding embA = getEmbedding(a);
//            Embedding embB = getEmbedding(b);
//            double similarity = cosineSimilarity(embA, embB);
//            return similarity >= 0.75;
//        } catch (Exception e) {
//            log.error("Lỗi khi so sánh embedding: {}", e.getMessage());
//            return true;
//        }
//    }
//}