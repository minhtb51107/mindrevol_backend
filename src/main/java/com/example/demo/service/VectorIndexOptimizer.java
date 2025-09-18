package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIndexOptimizer {

    private final JdbcTemplate jdbcTemplate;

    // Chạy mỗi 1 giờ (3600000ms)
    @Scheduled(fixedRate = 3600000)
    public void optimizeVectorIndex() {
        try {
            jdbcTemplate.execute("VACUUM ANALYZE message_embeddings");
            log.info("✅ Vector index optimization completed");
        } catch (Exception e) {
            log.warn("⚠️ Vector index optimization failed: {}", e.getMessage());
        }
    }
}
