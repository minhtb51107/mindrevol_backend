package com.example.demo.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheMaintenanceService {

    private final EntityManager entityManager;

    // Chạy vào 3 giờ sáng mỗi Chủ nhật
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void cleanupOldCacheEntries() {
        log.info("Starting scheduled cache cleanup task...");
        try {
            // Xóa các entry không được truy cập trong 90 ngày
            int deletedRows = entityManager.createNativeQuery(
                "DELETE FROM question_answer_cache WHERE last_accessed_at < (CURRENT_TIMESTAMP - INTERVAL '90 days')"
            ).executeUpdate();
            log.info("Successfully deleted {} old cache entries.", deletedRows);
        } catch (Exception e) {
            log.error("Error during cache cleanup task", e);
        }
    }
}