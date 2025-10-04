package com.example.demo.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String CACHE_PREFIX = "embedding_cache:";
    private static final long CACHE_TTL_DAYS = 30; // Thời gian sống của cache (30 ngày)

    @SneakyThrows
    public Optional<Embedding> getFromCache(String text) {
        String key = buildKey(text);
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            log.debug("[CACHE_HIT] Embedding for key: {}", key);
            Embedding embedding = objectMapper.readValue(json, new TypeReference<>() {});
            return Optional.of(embedding);
        }
        log.debug("[CACHE_MISS] Embedding for key: {}", key);
        return Optional.empty();
    }

    @SneakyThrows
    public void putToCache(String text, Embedding embedding) {
        String key = buildKey(text);
        String json = objectMapper.writeValueAsString(embedding);
        redisTemplate.opsForValue().set(key, json, CACHE_TTL_DAYS, TimeUnit.DAYS);
        log.debug("[CACHE_SET] Embedding for key: {}", key);
    }

    private String buildKey(String text) {
        return CACHE_PREFIX + sha256(text);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a standard algorithm and should always be available.
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }
}