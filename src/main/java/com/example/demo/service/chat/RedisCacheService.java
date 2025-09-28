package com.example.demo.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${application.cache.redis-ttl-seconds}")
    private long redisTtlSeconds;

    private static final String CACHE_PREFIX = "qa_cache:";

    public Optional<String> findAnswer(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(CACHE_PREFIX + key);
            if (value instanceof String) {
                log.debug("L2 Cache HIT for key: {}", key);
                return Optional.of((String) value);
            }
        } catch (Exception e) {
            log.error("Error reading from Redis cache for key: {}", key, e);
        }
        log.debug("L2 Cache MISS for key: {}", key);
        return Optional.empty();
    }

    public void saveAnswer(String key, String answer) {
        try {
            redisTemplate.opsForValue().set(
                CACHE_PREFIX + key, 
                answer, 
                Duration.ofSeconds(redisTtlSeconds)
            );
            log.debug("Saved to L2 Cache with key: {}", key);
        } catch (Exception e) {
            log.error("Error writing to Redis cache for key: {}", key, e);
        }
    }
    
 // ... các phương thức findAnswer và saveAnswer ...

    /**
     * ✅ THÊM MỚI: Xóa toàn bộ keys trong database Redis hiện tại.
     * Cần sử dụng cẩn thận trên môi trường production.
     */
    public void flushAllCache() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            log.warn("Flushed all keys from the current Redis database.");
        } catch (Exception e) {
            log.error("Failed to flush Redis cache.", e);
        }
    }
}