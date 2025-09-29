package com.example.demo.service.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
import com.example.demo.repository.chat.QuestionAnswerCacheProjection;
import com.example.demo.repository.chat.QuestionAnswerCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pgvector.PGvector;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAnswerCacheService {

    @Value("${application.cache.l2-distance-threshold}")
    private double l2DistanceThreshold;
    @Value("${application.cache.l1-cache-max-size}")
    private long l1CacheMaxSize;
    @Value("${application.cache.l1-cache-expire-minutes}")
    private long l1CacheExpireMinutes;

    private final QuestionAnswerCacheRepository cacheRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final RedisCacheService redisCacheService;

    private Cache<String, String> l1Cache;
    private static final String NEGATIVE_CACHE_SENTINEL = "NEGATIVE_CACHE_ENTRY";

    @PostConstruct
    public void init() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(l1CacheMaxSize)
                .expireAfterWrite(l1CacheExpireMinutes, TimeUnit.MINUTES)
                .build();
    }

    /**
     * ✅ ĐÃ SỬA LỖI: Chỉ sử dụng cho L1/L2 Cache Key.
     * Tạo chuỗi truy vấn có ngữ cảnh để tạo cache key cho Redis/Caffeine.
     * Điều này vẫn hữu ích để phân biệt các câu hỏi phụ thuộc ngữ cảnh.
     */
    private String createContextAwareQueryForKey(String question, String lastBotMessage) {
        if (lastBotMessage == null || lastBotMessage.isBlank()) {
            return question;
        }
        return "Assistant: " + lastBotMessage + "\n\nUser: " + question;
    }

    private String createCacheKey(String query) {
        return DigestUtils.md5DigestAsHex(query.getBytes());
    }

    @Transactional(readOnly = true)
    public Optional<String> findCachedAnswer(String question, String lastBotMessage) {
        // --- TẦNG 1 & 2: Vẫn sử dụng context-aware key ---
        String contextAwareQueryForKey = createContextAwareQueryForKey(question, lastBotMessage);
        String cacheKey = createCacheKey(contextAwareQueryForKey);

        String l1Result = l1Cache.getIfPresent(cacheKey);
        if (l1Result != null) {
            log.info("L1 Cache HIT for key: {}", cacheKey);
            return l1Result.equals(NEGATIVE_CACHE_SENTINEL) ? Optional.empty() : Optional.of(l1Result);
        }
        log.debug("L1 Cache MISS for key: {}", cacheKey);

        Optional<String> l2Result = redisCacheService.findAnswer(cacheKey);
        if (l2Result.isPresent()) {
            String answer = l2Result.get();
            l1Cache.put(cacheKey, answer);
            log.info("L2 Cache HIT for key: {}", cacheKey);
            return answer.equals(NEGATIVE_CACHE_SENTINEL) ? Optional.empty() : Optional.of(answer);
        }
        log.debug("L2 Cache MISS for key: {}", cacheKey);

        // --- TẦNG 3: ✅ SỬA LỖI - CHỈ SỬ DỤNG CÂU HỎI GỐC ĐỂ EMBEDDING ---
        // Sử dụng câu hỏi gốc của người dùng (question) để tìm kiếm vector.
        // Điều này cho kết quả tìm kiếm tương đồng chính xác hơn.
        log.debug("Using raw user query for L3 cache lookup: \"{}\"", question);
        Embedding embedding = embeddingModel.embed(question).content();
        PGvector pgVector = new PGvector(embedding.vector());
        List<QuestionAnswerCacheProjection> results = cacheRepository.findNearestNeighborsWithDistance(pgVector.toString(), 1);

        if (results.isEmpty()) {
            log.info("L3 Cache MISS: No valid neighbors found in DB.");
            l1Cache.put(cacheKey, NEGATIVE_CACHE_SENTINEL);
            redisCacheService.saveAnswer(cacheKey, NEGATIVE_CACHE_SENTINEL);
            return Optional.empty();
        }

        QuestionAnswerCacheProjection topResult = results.get(0);
        double distance = topResult.getDistance();

        if (distance <= l2DistanceThreshold) {
            String answer = topResult.getAnswerText();
            UUID cacheId = topResult.getId();
            
            if (answer == null || cacheId == null) {
                 log.warn("L3 Cache HIT but projection returned null fields. ID: {}. Treating as MISS.", cacheId);
                 l1Cache.put(cacheKey, NEGATIVE_CACHE_SENTINEL);
                 redisCacheService.saveAnswer(cacheKey, NEGATIVE_CACHE_SENTINEL);
                 return Optional.empty();
            }

            log.info("L3 Cache HIT! Found entry with ID: {}. Distance: {}", cacheId, distance);
            
            // Cập nhật L1/L2 bằng context-aware key để xử lý các câu hỏi phụ thuộc ngữ cảnh
            l1Cache.put(cacheKey, answer);
            redisCacheService.saveAnswer(cacheKey, answer);
            
            updateCacheAccessAsync(cacheId);
            return Optional.of(answer);
        } else {
            log.info("L3 Cache MISS: Nearest neighbor distance ({}) is above the threshold ({}).", distance, l2DistanceThreshold);
            l1Cache.put(cacheKey, NEGATIVE_CACHE_SENTINEL);
            redisCacheService.saveAnswer(cacheKey, NEGATIVE_CACHE_SENTINEL);
            return Optional.empty();
        }
    }

    @Transactional
    public void saveToCache(String question, String answer, String lastBotMessage, Map<String, Object> metadata, ZonedDateTime validUntil) {
        // Vẫn sử dụng context-aware key để lưu vào L1/L2
        String contextAwareQueryForKey = createContextAwareQueryForKey(question, lastBotMessage);
        String cacheKey = createCacheKey(contextAwareQueryForKey);
        
        l1Cache.put(cacheKey, answer);
        redisCacheService.saveAnswer(cacheKey, answer);
        
        // ✅ SỬA LỖI: CHỈ LƯU EMBEDDING CỦA CÂU HỎI GỐC VÀO L3
        log.debug("Using raw user query for L3 cache saving: \"{}\"", question);
        Embedding embedding = embeddingModel.embed(question).content();

        QuestionAnswerCache cacheEntry = new QuestionAnswerCache();
        cacheEntry.setId(UUID.randomUUID());
        cacheEntry.setQuestionText(question); // Lưu câu hỏi gốc
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
        log.info("Saved new entry to L3 cache (using raw query embedding). Valid until: {}", validUntil);
    }

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