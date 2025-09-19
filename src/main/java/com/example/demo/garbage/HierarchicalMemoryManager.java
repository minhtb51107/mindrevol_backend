//package com.example.demo.service.chat.memory;
//
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.model.chat.HierarchicalMemory;
//import com.example.demo.model.chat.MemorySummary;
//import com.example.demo.repository.chat.memory.HierarchicalMemoryRepository;
//import com.example.demo.repository.chat.memory.MemorySummaryRepo;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Set;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class HierarchicalMemoryManager {
//    
//    private static final int SEGMENTS_PER_LEVEL = 10; // Mỗi level tổng hợp 10 segments
//    
//    private final HierarchicalMemoryRepository hierarchicalMemoryRepository;
//    private final MemorySummaryRepo memorySummaryRepo;
//    private final ChatLanguageModel chatLanguageModel;
//    
//    // Thêm cache
//    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();
//    private final Map<String, LocalDateTime> cacheTimestamps = new ConcurrentHashMap<>();
//    private static final long CACHE_DURATION_HOURS = 24;
//    
//    private final Map<Long, LocalDateTime> lastApiCallBySession = new ConcurrentHashMap<>();
//
//    
//    private boolean canCallApi(ChatSession session) {
//        LocalDateTime lastCall = lastApiCallBySession.get(session.getId());
//        return lastCall == null || 
//               lastCall.isBefore(LocalDateTime.now().minusMinutes(30));
//    }
//
//    private String getCachedSummary(String content) {
//        String cacheKey = Integer.toString(content.hashCode());
//        String cached = summaryCache.get(cacheKey);
//        
//        if (cached != null && cacheTimestamps.get(cacheKey)
//            .isAfter(LocalDateTime.now().minusHours(CACHE_DURATION_HOURS))) {
//            return cached;
//        }
//        return null;
//    }
//
//    private void cacheSummary(String content, String summary) {
//        String cacheKey = Integer.toString(content.hashCode());
//        summaryCache.put(cacheKey, summary);
//        cacheTimestamps.put(cacheKey, LocalDateTime.now());
//    }
//
//    private boolean isContentSimilarToPrevious(String newContent) {
//        if (newContent.length() < 50) return false; // Bỏ qua content quá ngắn
//        
//        return summaryCache.values().stream()
//            .anyMatch(cached -> {
//                double similarity = calculateSimilarity(cached, newContent);
//                // ✅ Adjust threshold xuống 65%
//                return similarity > 0.65 && 
//                       Math.abs(cached.length() - newContent.length()) < 100; // Độ dài tương đương
//            });
//    }
//    
//    private double calculateSimilarity(String text1, String text2) {
//        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
//            return 0.0;
//        }
//        
//        // Tạo word frequency maps
//        Map<String, Integer> freq1 = getWordFrequency(text1);
//        Map<String, Integer> freq2 = getWordFrequency(text2);
//        
//        // Lấy tất cả các từ duy nhất
//        Set<String> allWords = new HashSet<>();
//        allWords.addAll(freq1.keySet());
//        allWords.addAll(freq2.keySet());
//        
//        // Tính dot product và magnitudes
//        double dotProduct = 0;
//        double mag1 = 0;
//        double mag2 = 0;
//        
//        for (String word : allWords) {
//            int count1 = freq1.getOrDefault(word, 0);
//            int count2 = freq2.getOrDefault(word, 0);
//            
//            dotProduct += count1 * count2;
//            mag1 += count1 * count1;
//            mag2 += count2 * count2;
//        }
//        
//        // Tính cosine similarity
//        if (mag1 == 0 || mag2 == 0) return 0.0;
//        
//        return dotProduct / (Math.sqrt(mag1) * Math.sqrt(mag2));
//    }
//
//    private Map<String, Integer> getWordFrequency(String text) {
//        Map<String, Integer> frequency = new HashMap<>();
//        String[] words = text.toLowerCase().split("\\s+");
//        
//        for (String word : words) {
//            // Loại bỏ punctuation cơ bản
//            word = word.replaceAll("[^a-zA-Z0-9À-ỹ]", "");
//            if (!word.isEmpty()) {
//                frequency.put(word, frequency.getOrDefault(word, 0) + 1);
//            }
//        }
//        
//        return frequency;
//    }
//    
//    /**
//     * Kiểm tra và tạo summary phân cấp khi cần
//     */
//    @Async("ioTaskExecutor")
//    public CompletableFuture<Void> checkAndCreateHierarchicalSummary(ChatSession session, int currentSegment) {
//        return CompletableFuture.runAsync(() -> {
//            try {
//                // Kiểm tra xem đã đủ segments để tạo summary cấp cao chưa
//                if (currentSegment % SEGMENTS_PER_LEVEL == 0) {
//                    createHigherLevelSummary(session, currentSegment);
//                }
//                
//                // Kiểm tra và xây dựng toàn bộ hierarchy nếu cần
//                buildFullHierarchy(session);
//                
//            } catch (Exception e) {
//                log.error("Lỗi khi tạo hierarchical summary cho session {}: {}", session.getId(), e.getMessage());
//            }
//        });
//    }
//    
//    /**
//     * Tạo summary cấp cao từ các summaries cấp thấp
//     */
//    @Transactional
//    public void createHigherLevelSummary(ChatSession session, int upToSegment) {
//        int targetLevel = calculateTargetLevel(upToSegment);
//        int segmentStart = findSegmentStartForLevel(upToSegment, targetLevel);
//        int segmentEnd = segmentStart + SEGMENTS_PER_LEVEL - 1;
//        
//        // Kiểm tra xem summary đã tồn tại chưa
//        Optional<HierarchicalMemory> existingSummary = hierarchicalMemoryRepository
//                .findByChatSessionAndHierarchyLevelAndSegmentStartAndSegmentEnd(
//                        session, targetLevel, segmentStart, segmentEnd);
//        
//        if (existingSummary.isPresent()) {
//            log.debug("Summary cấp {} đã tồn tại cho segments {}-{}", 
//                    targetLevel, segmentStart, segmentEnd);
//            return;
//        }
//        
//        // Lấy các summaries cấp thấp hơn
//        List<?> childSummaries = getChildSummaries(session, targetLevel - 1, segmentStart, segmentEnd);
//        
//        if (childSummaries.isEmpty()) {
//            log.warn("Không tìm thấy child summaries để tạo summary cấp {}", targetLevel);
//            return;
//        }
//        
//        // Tạo summary từ các child summaries
//        String combinedContent = combineSummaries(childSummaries);
//        String higherLevelSummary = generateHigherLevelSummary(combinedContent, session);
//        
//        // Lưu summary mới
//        HierarchicalMemory newSummary = HierarchicalMemory.builder()
//                .chatSession(session)
//                .hierarchyLevel(targetLevel)
//                .segmentStart(segmentStart)
//                .segmentEnd(segmentEnd)
//                .summaryContent(higherLevelSummary)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//        
//        hierarchicalMemoryRepository.save(newSummary);
//        
//        log.info("Đã tạo summary cấp {} cho segments {}-{}", targetLevel, segmentStart, segmentEnd);
//    }
//    
//    /**
//     * Xây dựng toàn bộ hierarchy từ các summaries hiện có
//     */
//    @Transactional
//    public void buildFullHierarchy(ChatSession session) {
//        // Lấy tất cả leaf summaries (cấp 0)
//        List<MemorySummary> leafSummaries = memorySummaryRepo.findAllByChatSessionOrderByTopicSegmentAsc(session);
//        
//        if (leafSummaries.isEmpty()) {
//            return;
//        }
//        
//        int maxSegment = leafSummaries.stream()
//                .mapToInt(MemorySummary::getTopicSegment)
//                .max()
//                .orElse(0);
//        
//        // Xây dựng hierarchy từ dưới lên
//        for (int level = 1; level <= calculateMaxLevel(maxSegment); level++) {
//            buildLevel(session, level);
//        }
//    }
//    
//    /**
//     * Xây dựng một level cụ thể của hierarchy
//     */
//    private void buildLevel(ChatSession session, int level) {
//        int segmentsInLevel = (int) Math.pow(SEGMENTS_PER_LEVEL, level);
//        
//        // Tìm range của segments cho level này
//        List<HierarchicalMemory> lowerLevelSummaries = hierarchicalMemoryRepository
//                .findByChatSessionAndHierarchyLevel(session, level - 1);
//        
//        if (lowerLevelSummaries.isEmpty()) {
//            return;
//        }
//        
//        int minSegment = lowerLevelSummaries.stream()
//                .mapToInt(HierarchicalMemory::getSegmentStart)
//                .min()
//                .orElse(0);
//        
//        int maxSegment = lowerLevelSummaries.stream()
//                .mapToInt(HierarchicalMemory::getSegmentEnd)
//                .max()
//                .orElse(0);
//        
//        // Tạo summaries cho level này
//        for (int segmentStart = minSegment; segmentStart <= maxSegment; segmentStart += segmentsInLevel) {
//            int segmentEnd = Math.min(segmentStart + segmentsInLevel - 1, maxSegment);
//            
//            Optional<HierarchicalMemory> existing = hierarchicalMemoryRepository
//                    .findByChatSessionAndHierarchyLevelAndSegmentStartAndSegmentEnd(
//                            session, level, segmentStart, segmentEnd);
//            
//            if (existing.isEmpty()) {
//                createSummaryForLevel(session, level, segmentStart, segmentEnd);
//            }
//        }
//    }
//    
//    /**
//     * Tạo summary cho một level cụ thể
//     */
//    private void createSummaryForLevel(ChatSession session, int level, int segmentStart, int segmentEnd) {
//        List<?> childSummaries = getChildSummaries(session, level - 1, segmentStart, segmentEnd);
//        
//        if (childSummaries.isEmpty()) {
//            return;
//        }
//        
//        String combinedContent = combineSummaries(childSummaries);
//        String levelSummary = generateHigherLevelSummary(combinedContent, session);
//        
//        HierarchicalMemory summary = HierarchicalMemory.builder()
//                .chatSession(session)
//                .hierarchyLevel(level)
//                .segmentStart(segmentStart)
//                .segmentEnd(segmentEnd)
//                .summaryContent(levelSummary)
//                .createdAt(LocalDateTime.now())
//                .updatedAt(LocalDateTime.now())
//                .build();
//        
//        hierarchicalMemoryRepository.save(summary);
//    }
//
//    /**
//     * Lấy các summaries cấp thấp hơn
//     */
//    private List<?> getChildSummaries(ChatSession session, int childLevel, int segmentStart, int segmentEnd) {
//        if (childLevel == 0) {
//            // ✅ LEVEL 0: Lấy từ MemorySummary (leaf nodes)
//            return memorySummaryRepo.findByChatSessionAndTopicSegmentBetween(session, segmentStart, segmentEnd);
//        } else {
//            // ✅ LEVEL >= 1: Lấy từ HierarchicalMemory
//            return hierarchicalMemoryRepository.findByChatSessionAndHierarchyLevel(session, childLevel).stream()
//                    .filter(summary -> summary.getSegmentStart() >= segmentStart && summary.getSegmentEnd() <= segmentEnd)
//                    .toList();
//        }
//    }
//    
//    /**
//     * Kết hợp nội dung các summaries
//     */
//    /**
//     * Kết hợp nội dung các summaries (hỗ trợ cả MemorySummary và HierarchicalMemory)
//     */
//    private String combineSummaries(List<?> summaries) {
//        StringBuilder combined = new StringBuilder();
//        
//        for (Object summary : summaries) {
//            if (summary instanceof MemorySummary) {
//                MemorySummary ms = (MemorySummary) summary;
//                combined.append("Segment ").append(ms.getTopicSegment())
//                       .append(": ").append(ms.getSummaryContent())
//                       .append("\n\n");
//            } else if (summary instanceof HierarchicalMemory) {
//                HierarchicalMemory hm = (HierarchicalMemory) summary;
//                combined.append("Segments ").append(hm.getSegmentStart())
//                       .append("-").append(hm.getSegmentEnd())
//                       .append(" (Level ").append(hm.getHierarchyLevel()).append("): ")
//                       .append(hm.getSummaryContent())
//                       .append("\n\n");
//            }
//        }
//        
//        return combined.toString();
//    }
//    
//    /**
//     * Tạo summary cấp cao từ nội dung kết hợp
//     */
//    private String generateHigherLevelSummary(String combinedContent, ChatSession session) {
//        if (!canCallApi(session)) {
//            return "Tóm tắt tạm thời: " + combinedContent.substring(0, Math.min(200, combinedContent.length()));
//        }
//        // Kiểm tra nếu content quá ngắn thì không cần gọi AI
//        if (combinedContent == null || combinedContent.length() < 100) {
//            return "Tóm tắt tổng quan: " + combinedContent;
//        }
//        
//        // Kiểm tra nếu content không thay đổi nhiều so với lần trước
//        if (isContentSimilarToPrevious(combinedContent)) {
//            return getCachedSummary(combinedContent);
//        }
//        
//        try {
//            String prompt = "Bạn là trợ lý tóm tắt chuyên nghiệp. Hãy tạo một bản tóm tắt cấp cao " +
//                    "từ các bản tóm tắt con sau đây. Tập trung vào các ý chính, xu hướng, " +
//                    "và chủ đề quan trọng xuyên suốt.\n\n" +
//                    "Tạo bản tóm tắt cấp cao từ các tóm tắt sau:\n\n" + combinedContent;
//            
//            String summary = chatLanguageModel.generate(prompt);
//            
//            lastApiCallBySession.put(session.getId(), LocalDateTime.now());
//            
//            // ✅ BỎ COMMENT - CẬP NHẬT CACHE
//            cacheSummary(combinedContent, summary);
//            
//            return summary;
//        } catch (Exception e) {
//            log.warn("Không thể tạo higher-level summary: {}", e.getMessage());
//            return "Tóm tắt tổng quan: " + combinedContent.substring(0, Math.min(500, combinedContent.length()));
//        }
//    }
//    
//    /**
//     * Truy xuất hierarchical memory cho prompt
//     */
//    public String getHierarchicalContext(ChatSession session, int currentSegment, String query) {
//        StringBuilder context = new StringBuilder();
//        
//        // Thêm summary cấp cao nhất trước
//        Optional<HierarchicalMemory> highestLevel = hierarchicalMemoryRepository
//                .findByChatSessionOrderByHierarchyLevelDescSegmentStartAsc(session)
//                .stream()
//                .findFirst();
//        
//        highestLevel.ifPresent(summary -> 
//            context.append("Tổng quan cuộc trò chuyện:\n")
//                  .append(summary.getSummaryContent())
//                  .append("\n\n"));
//        
//        // Thêm summaries cấp trung gian liên quan
//        addRelevantSummaries(context, session, currentSegment, query);
//        
//        return context.toString();
//    }
//    
//    /**
//     * Thêm các summaries liên quan dựa trên segment và query
//     */
//    private void addRelevantSummaries(StringBuilder context, ChatSession session, int currentSegment, String query) {
//        // Lấy các summaries ở các level khác nhau
//    	for (int level = 0; level <= calculateMaxLevel(currentSegment); level++) {
//    	    final int currentLevel = level; // ✅ biến final để dùng trong lambda
//    	    Optional<HierarchicalMemory> summary = hierarchicalMemoryRepository
//    	            .findSummaryForSegmentAndLevel(session, currentLevel, currentSegment);
//    	    
//    	    summary.ifPresent(s -> {
//    	        context.append("Chi tiết level ").append(currentLevel).append(":\n")
//    	              .append(s.getSummaryContent())
//    	              .append("\n\n");
//    	    });
//    	}
//
//    }
//    
//    // Các phương thức helper
//    private int calculateTargetLevel(int segment) {
//        return (int) Math.floor(Math.log(segment + 1) / Math.log(SEGMENTS_PER_LEVEL));
//    }
//    
//    private int calculateMaxLevel(int maxSegment) {
//        return maxSegment == 0 ? 0 : (int) Math.floor(Math.log(maxSegment + 1) / Math.log(SEGMENTS_PER_LEVEL));
//    }
//    
//    private int findSegmentStartForLevel(int segment, int level) {
//        int segmentsInLevel = (int) Math.pow(SEGMENTS_PER_LEVEL, level);
//        return (segment / segmentsInLevel) * segmentsInLevel;
//    }
//}

