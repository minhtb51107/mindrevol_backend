//package com.example.demo.service.chat.memory;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.service.chat.util.EmbeddingService;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class CachedEmbeddingService {
//    private final EmbeddingService embeddingService;
//    private final Cache<String, List<Double>> embeddingCache;
//    private final Cache<Long, List<Double>> messageEmbeddingCache;
//
//    public CachedEmbeddingService(EmbeddingService embeddingService) {
//        this.embeddingService = embeddingService;
//        
//        // ✅ Khởi tạo Caffeine cache với cấu hình
//        this.embeddingCache = Caffeine.newBuilder()
//            .maximumSize(1000)
//            .expireAfterWrite(1, TimeUnit.HOURS)
//            .build();
//            
//        this.messageEmbeddingCache = Caffeine.newBuilder()
//            .maximumSize(5000)
//            .expireAfterWrite(24, TimeUnit.HOURS)
//            .build();
//    }
//
//    public List<Double> getCachedEmbedding(String text) {
//        // ✅ Sử dụng hash code làm key để tránh key quá dài
//        String key = "embedding:" + text.hashCode();
//        return embeddingCache.get(key, k -> {
//            try {
//                return embeddingService.getEmbedding(text);
//            } catch (Exception e) {
//                // ✅ Trả về list rỗng thay vì null để tránh NPE
//                return Collections.emptyList();
//            }
//        });
//    }
//
//    public List<Double> getCachedMessageEmbedding(Long messageId, String content) {
//        return messageEmbeddingCache.get(messageId, k -> {
//            try {
//                return embeddingService.getEmbedding(content);
//            } catch (Exception e) {
//                // ✅ Trả về list rỗng thay vì null để tránh NPE
//                return Collections.emptyList();
//            }
//        });
//    }
//
//    // ✅ Preload embeddings for frequent messages
//    public void preloadEmbeddings(List<ChatMessage> messages) {
//        messages.forEach(msg -> {
//            if (!messageEmbeddingCache.asMap().containsKey(msg.getId())) {
//                messageEmbeddingCache.put(msg.getId(), 
//                    getCachedMessageEmbedding(msg.getId(), msg.getContent()));
//            }
//        });
//    }
//
//    @Scheduled(fixedRate = 3600000) // Clear cache mỗi giờ
//    public void clearCache() {
//        embeddingCache.invalidateAll();
//        messageEmbeddingCache.invalidateAll();
//        System.out.println("✅ Embedding cache cleared");
//    }
//    
//    // ✅ Thêm phương thức tiện ích để lấy thông tin cache
//    public long getEmbeddingCacheSize() {
//        return embeddingCache.estimatedSize();
//    }
//    
//    public long getMessageEmbeddingCacheSize() {
//        return messageEmbeddingCache.estimatedSize();
//    }
//    
//    // ✅ Phương thức để xóa cache cụ thể
//    public void invalidateEmbedding(String text) {
//        String key = "embedding:" + text.hashCode();
//        embeddingCache.invalidate(key);
//    }
//    
//    public void invalidateMessageEmbedding(Long messageId) {
//        messageEmbeddingCache.invalidate(messageId);
//    }
//}