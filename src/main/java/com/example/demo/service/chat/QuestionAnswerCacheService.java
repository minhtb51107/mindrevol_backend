package com.example.demo.service.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
import com.example.demo.repository.chat.QuestionAnswerCacheProjection;
import com.example.demo.repository.chat.QuestionAnswerCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

// ✅ THÊM CÁC IMPORT CẦN THIẾT
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAnswerCacheService {

    // --- Các cấu hình đã có ---
    @Value("${application.cache.l2-distance-threshold}")
    private double l2DistanceThreshold;
    
    // ✅ THÊM CẤU HÌNH MỚI TỪ application.yml
    @Value("${application.cache.l1-cache-max-size}")
    private long l1CacheMaxSize;
    @Value("${application.cache.l1-cache-expire-minutes}")
    private long l1CacheExpireMinutes;

    // --- Các dependencies đã có ---
    private final QuestionAnswerCacheRepository cacheRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final RedisCacheService redisCacheService;

    // ✅ THÊM MỚI: L1 In-Memory Cache
    private Cache<String, String> l1Cache;

    // ✅ THÊM MỚI: Sentinel value cho Negative Cache
    private static final String NEGATIVE_CACHE_SENTINEL = "NEGATIVE_CACHE_ENTRY";

    /**
     * ✅ THÊM MỚI: Khởi tạo L1 cache sau khi các properties được tiêm vào.
     */
    @PostConstruct
    public void init() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(l1CacheMaxSize)
                .expireAfterWrite(l1CacheExpireMinutes, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Giữ nguyên: Tạo chuỗi truy vấn có ngữ cảnh.
     */
    private String createContextAwareQuery(String question, String lastBotMessage) {
        if (lastBotMessage == null || lastBotMessage.isBlank()) {
            return question;
        }
        return "Assistant: " + lastBotMessage + "\n\nUser: " + question;
    }

    /**
     * Giữ nguyên: Tạo key cho Redis/Caffeine cache.
     */
    private String createCacheKey(String query) {
        return DigestUtils.md5DigestAsHex(query.getBytes());
    }

    /**
     * ✅ ĐÃ NÂNG CẤP HOÀN CHỈNH: Tích hợp L1 + L2 + L3 và Negative Cache.
     */
    @Transactional(readOnly = true)
    public Optional<String> findCachedAnswer(String question, String lastBotMessage) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        String cacheKey = createCacheKey(contextAwareQuery);

        // --- TẦNG 1: KIỂM TRA L1 CACHE (Caffeine) ---
        String l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.info("L1 Cache HIT for key: {}", cacheKey);
            // Kiểm tra negative cache hit
            return l1Result.equals(NEGATIVE_CACHE_SENTINEL) ? Optional.empty() : Optional.of(l1Result);
        }
        log.debug("L1 Cache MISS for key: {}", cacheKey);

        // --- TẦNG 2: KIỂM TRA L2 CACHE (Redis) ---
        Optional<String> l2Result = redisCacheService.findAnswer(cacheKey);
        if (l2Result.isPresent()) {
            String answer = l2Result.get();
            l1Cache.put(cacheKey, answer); // Cập nhật lại L1 cache
            // Kiểm tra negative cache hit
            return answer.equals(NEGATIVE_CACHE_SENTINEL) ? Optional.empty() : Optional.of(answer);
        }
        
        // --- TẦNG 3: KIỂM TRA L3 CACHE (PostgreSQL) ---
        log.debug("Context-aware query for L3 cache lookup: \"{}\"", contextAwareQuery.replace("\n", "\\n"));
        Embedding embedding = embeddingModel.embed(contextAwareQuery).content();
        PGvector pgVector = new PGvector(embedding.vector());
        List<QuestionAnswerCacheProjection> results = cacheRepository.findNearestNeighborsWithDistance(pgVector.toString(), 1);

        if (results.isEmpty()) {
            log.info("L3 Cache MISS: No valid neighbors found in DB.");
            // Ghi nhận Negative Cache vào L1 và L2
            l1Cache.put(cacheKey, NEGATIVE_CACHE_SENTINEL);
            redisCacheService.saveAnswer(cacheKey, NEGATIVE_CACHE_SENTINEL);
            return Optional.empty();
        }

        QuestionAnswerCacheProjection topResult = results.get(0);
        double distance = topResult.getDistance();

        if (distance <= l2DistanceThreshold) {
            QuestionAnswerCache cacheEntry = topResult.getQuestionAnswerCache();
            String answer = cacheEntry.getAnswerText();
            log.info("L3 Cache HIT! Found entry with ID: {}. Distance: {}", cacheEntry.getId(), distance);
            
            // Cập nhật cho L1 và L2 cache
            l1Cache.put(cacheKey, answer);
            redisCacheService.saveAnswer(cacheKey, answer);
            
            updateCacheAccessAsync(cacheEntry.getId());
            return Optional.of(answer);
        } else {
            log.info("L3 Cache MISS: Nearest neighbor distance ({}) is above the threshold ({}).", distance, l2DistanceThreshold);
            // Ghi nhận Negative Cache vào L1 và L2
            l1Cache.put(cacheKey, NEGATIVE_CACHE_SENTINEL);
            redisCacheService.saveAnswer(cacheKey, NEGATIVE_CACHE_SENTINEL);
            return Optional.empty();
        }
    }

    /**
     * ✅ ĐÃ NÂNG CẤP HOÀN CHỈNH: Lưu vào cả 3 lớp cache.
     */
    @Transactional
    public void saveToCache(String question, String answer, String lastBotMessage, Map<String, Object> metadata, ZonedDateTime validUntil) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        String cacheKey = createCacheKey(contextAwareQuery);
        
        // --- LƯU VÀO CẢ 3 LỚP CACHE ---
        // 1. Lưu vào L1 (Caffeine)
        l1Cache.put(cacheKey, answer);
        // 2. Lưu vào L2 (Redis)
        redisCacheService.saveAnswer(cacheKey, answer);
        // 3. Lưu vào L3 (PostgreSQL)
        log.debug("Context-aware query for L3 cache saving: \"{}\"", contextAwareQuery.replace("\n", "\\n"));
        Embedding embedding = embeddingModel.embed(contextAwareQuery).content();

        QuestionAnswerCache cacheEntry = new QuestionAnswerCache();
        cacheEntry.setId(UUID.randomUUID());
        cacheEntry.setQuestionText(question);
        cacheEntry.setAnswerText(answer);
        cacheEntry.setQuestionEmbedding(new PGvector(embedding.vector()));
        
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            cacheEntry.setMetadata(metadataJson);
        } catch (JsonProcessingException e) {
            log.error("Error serializing metadata to JSON for question: {}", question, e);
            cacheEntry.setMetadata("{}");
        }

        cacheEntry.setValidUntil(validUntil);
        cacheEntry.setCreatedAt(ZonedDateTime.now());
        cacheEntry.setLastAccessedAt(ZonedDateTime.now());
        cacheEntry.setAccessCount(1);

        cacheRepository.save(cacheEntry);
        log.info("Saved new context-aware entry to L3 cache. Valid until: {}", validUntil);
    }

    /**
     * ✅ GIỮ NGUYÊN: Cập nhật thông tin truy cập cache bất đồng bộ.
     */
    @Async
    @Transactional
    public void updateCacheAccessAsync(UUID cacheId) {
        cacheRepository.findById(cacheId).ifPresent(cache -> {
            cache.setAccessCount(cache.getAccessCount() + 1);
            cache.setLastAccessedAt(ZonedDateTime.now());
            cacheRepository.save(cache);
        });
    }
}