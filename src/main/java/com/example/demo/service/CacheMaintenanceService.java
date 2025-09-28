package com.example.demo.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheMaintenanceService {

    private final EntityManager entityManager;

    // ✅ ĐỌC GIÁ TRỊ TỪ FILE CẤU HÌNH
    @Value("${application.cache.cleanup-interval-days}")
    private int cleanupIntervalDays;

    // Chạy vào 3 giờ sáng mỗi Chủ nhật
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void cleanupOldCacheEntries() {
        log.info("Starting scheduled cache cleanup task for entries older than {} days...", cleanupIntervalDays);
        try {
            // ✅ SỬ DỤNG BIẾN CẤU HÌNH
            String queryString = String.format(
                "DELETE FROM question_answer_cache WHERE last_accessed_at < (CURRENT_TIMESTAMP - INTERVAL '%d days')",
                cleanupIntervalDays
            );
            int deletedRows = entityManager.createNativeQuery(queryString).executeUpdate();
            
            log.info("Successfully deleted {} old cache entries.", deletedRows);
        } catch (Exception e) {
            log.error("Error during cache cleanup task", e);
        }
    }
}