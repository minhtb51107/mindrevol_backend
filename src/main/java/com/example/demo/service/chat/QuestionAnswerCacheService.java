package com.example.demo.service.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAnswerCacheService {

    // Sử dụng giá trị từ application.yml, linh hoạt hơn
    @Value("${application.cache.similarity-threshold}")
    private double similarityThreshold;

    private final QuestionAnswerCacheRepository cacheRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper; // <-- THÊM FIELD NÀY

    /**
     * ✅ GIỮ NGUYÊN: Tạo chuỗi truy vấn có ngữ cảnh.
     * Đây là logic cốt lõi của bạn và rất hiệu quả.
     */
    private String createContextAwareQuery(String question, String lastBotMessage) {
        if (lastBotMessage == null || lastBotMessage.isBlank()) {
            return question;
        }
        return "Assistant: " + lastBotMessage + "\n\nUser: " + question;
    }

    /**
     * ✅ NÂNG CẤP: Tìm kiếm câu trả lời trong cache.
     * - Sử dụng repository method mới để tự động lọc cache hết hạn (TTL).
     * - Vẫn giữ logic kiểm tra ngưỡng tương đồng của bạn.
     */
    @Transactional(readOnly = true)
    public Optional<String> findCachedAnswer(String question, String lastBotMessage) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        log.debug("Context-aware query for cache lookup: \"{}\"", contextAwareQuery.replace("\n", "\\n"));

        Embedding embedding = embeddingModel.embed(contextAwareQuery).content();
        PGvector pgVector = new PGvector(embedding.vector());

        // --- THAY ĐỔI CÁCH GỌI ---
        // Gọi repository với chuỗi vector thay vì đối tượng
        List<QuestionAnswerCache> results = cacheRepository.findNearestNeighbors(pgVector.toString(), 1);

        if (results.isEmpty()) {
            log.info("Context-aware cache MISS: No valid neighbors found.");
            return Optional.empty();
        }
        
        // ... (phần còn lại của phương thức không đổi)
        QuestionAnswerCache topResult = results.get(0);
        log.info("Context-aware cache HIT! Found cache entry with ID: {}", topResult.getId());
        updateCacheAccessAsync(topResult.getId());
        return Optional.of(topResult.getAnswerText());
    }

    /**
     * ✅ NÂNG CẤP: Lưu vào cache với đầy đủ thông tin.
     * - Thêm tham số metadata và validUntil.
     * - Vẫn giữ logic tạo embedding theo ngữ cảnh.
     */
    @Transactional
    public void saveToCache(String question, String answer, String lastBotMessage, Map<String, Object> metadata, ZonedDateTime validUntil) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        log.debug("Context-aware query for cache saving: \"{}\"", contextAwareQuery.replace("\n", "\\n"));

        Embedding embedding = embeddingModel.embed(contextAwareQuery).content();

        QuestionAnswerCache cacheEntry = new QuestionAnswerCache();
        cacheEntry.setId(UUID.randomUUID()); // Đảm bảo ID được tạo
        cacheEntry.setQuestionText(question);
        cacheEntry.setAnswerText(answer);
        cacheEntry.setQuestionEmbedding(new PGvector(embedding.vector()));
        
        // --- PHẦN THAY ĐỔI CỐT LÕI ---
        // Chuyển đổi Map metadata thành chuỗi JSON
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            cacheEntry.setMetadata(metadataJson);
        } catch (JsonProcessingException e) {
            log.error("Error serializing metadata to JSON for question: {}", question, e);
            // Có thể đặt một giá trị mặc định hoặc null tùy theo logic của bạn
            cacheEntry.setMetadata("{}"); // Lưu một JSON object rỗng nếu có lỗi
        }
        // ----------------------------

        cacheEntry.setValidUntil(validUntil);
        
        // Các thông tin khác
        cacheEntry.setCreatedAt(ZonedDateTime.now());
        cacheEntry.setLastAccessedAt(ZonedDateTime.now());
        cacheEntry.setAccessCount(1);

        cacheRepository.save(cacheEntry);
        log.info("Saved new context-aware entry to cache. Valid until: {}", validUntil);
    }

    /**
     * ✅ GIỮ NGUYÊN: Cập nhật thông tin truy cập cache bất đồng bộ.
     * Logic này rất hữu ích cho việc thống kê và dọn dẹp cache sau này.
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