package com.example.demo.service.chat;

import com.example.demo.repository.chat.QuestionAnswerCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheInvalidationService {

    private final QuestionAnswerCacheRepository cacheRepository;
    private final RedisCacheService redisCacheService; // Giả sử bạn có service này

    /**
     * Vô hiệu hóa cache cho một tài liệu cụ thể.
     * @param documentId ID của tài liệu đã được cập nhật hoặc xóa.
     */
    @Transactional
    public void invalidateCacheForDocument(String documentId) {
        log.warn("Starting cache invalidation for document ID: {}", documentId);

        // 1. Xóa khỏi L3 Cache (PostgreSQL)
        try {
            cacheRepository.deleteAllByDocumentId(documentId);
            log.info("Successfully invalidated L3 cache entries for document ID: {}", documentId);
        } catch (Exception e) {
            log.error("Error invalidating L3 cache for document ID: {}", documentId, e);
        }

        // 2. Xóa toàn bộ L2 Cache (Redis)
        // Đây là cách tiếp cận an toàn nhất vì tìm kiếm theo value trong Redis rất tốn kém.
        // Chấp nhận rằng các cache không liên quan cũng bị xóa để đảm bảo tính nhất quán.
        try {
            redisCacheService.flushAllCache();
            log.warn("Flushed all L2 Redis cache to ensure data consistency after document update.");
        } catch (Exception e) {
            log.error("Error flushing L2 Redis cache during invalidation for document ID: {}", documentId, e);
        }
    }
}