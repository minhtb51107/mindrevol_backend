package com.example.demo.service.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
import com.example.demo.repository.chat.QuestionAnswerCacheRepository;
import com.pgvector.PGvector;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j // Thêm logging
@Service
@RequiredArgsConstructor
public class QuestionAnswerCacheService {

    private static final double SIMILARITY_THRESHOLD = 0.98;

    private final QuestionAnswerCacheRepository cacheRepository;
    private final EmbeddingModel embeddingModel;

    /**
     * ✅ TẠO CHUỖI TRUY VẤN CÓ NGỮ CẢNH
     * Kết hợp tin nhắn cuối cùng của bot và câu hỏi của người dùng.
     */
    private String createContextAwareQuery(String question, String lastBotMessage) {
        if (lastBotMessage == null || lastBotMessage.isBlank()) {
            return question;
        }
        // Dùng một ký tự phân cách đặc biệt để LLM hiểu rõ ranh giới
        return "Assistant: " + lastBotMessage + "\n\nUser: " + question;
    }

    /**
     * ✅ SỬA LẠI PHƯƠNG THỨC ĐỂ NHẬN THÊM lastBotMessage
     */
    @Transactional(readOnly = true)
    public Optional<String> findCachedAnswer(String question, String lastBotMessage) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        log.debug("Context-aware query for cache lookup: \"{}\"", contextAwareQuery.replace("\n", "\\n"));

        float[] embedding = embeddingModel.embed(contextAwareQuery).content().vector();
        List<Object[]> results = cacheRepository.findMostSimilarQuestion(new PGvector(embedding).toString());

        if (results.isEmpty()) {
            return Optional.empty();
        }

        Object[] topResult = results.get(0);
        double similarity = (Double) topResult[2];

        if (similarity > SIMILARITY_THRESHOLD) {
            UUID cacheId = (UUID) topResult[0];
            updateCacheAccessAsync(cacheId);
            log.info("Context-aware cache HIT with similarity: {}", similarity);
            return Optional.of((String) topResult[1]);
        }

        log.info("Context-aware cache MISS with similarity: {}", similarity);
        return Optional.empty();
    }

    /**
     * ✅ SỬA LẠI PHƯƠNG THỨC ĐỂ NHẬN THÊM lastBotMessage
     */
    @Transactional
    public void saveToCache(String question, String answer, String lastBotMessage) {
        String contextAwareQuery = createContextAwareQuery(question, lastBotMessage);
        log.debug("Context-aware query for cache saving: \"{}\"", contextAwareQuery.replace("\n", "\\n"));

        float[] embedding = embeddingModel.embed(contextAwareQuery).content().vector();

        QuestionAnswerCache cacheEntry = new QuestionAnswerCache();
        cacheEntry.setId(UUID.randomUUID());
        // Vẫn lưu câu hỏi gốc để dễ đọc và debug
        cacheEntry.setQuestionText(question);
        cacheEntry.setAnswerText(answer);
        cacheEntry.setQuestionEmbedding(new PGvector(embedding));

        cacheRepository.save(cacheEntry);
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