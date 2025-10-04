package com.example.demo.service.chat.integration;

import com.example.demo.service.chat.EmbeddingCacheService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class CachedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final EmbeddingCacheService cacheService;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> finalEmbeddings = new ArrayList<>();
        List<TextSegment> segmentsToEmbed = new ArrayList<>();
        int inputTokenCount = 0;

        // 1. Kiểm tra cache trước
        for (TextSegment segment : textSegments) {
            Optional<Embedding> cachedEmbedding = cacheService.getFromCache(segment.text());
            if (cachedEmbedding.isPresent()) {
                finalEmbeddings.add(cachedEmbedding.get());
            } else {
                segmentsToEmbed.add(segment);
                finalEmbeddings.add(null); // Placeholder
            }
        }

        // 2. Gọi API cho những text chưa có trong cache
        if (!segmentsToEmbed.isEmpty()) {
            Response<List<Embedding>> response = delegate.embedAll(segmentsToEmbed);
            List<Embedding> newEmbeddings = response.content();
            TokenUsage usage = response.tokenUsage();
            if(usage != null) {
                inputTokenCount = usage.inputTokenCount();
            }


            int newEmbeddingIndex = 0;
            for (int i = 0; i < finalEmbeddings.size(); i++) {
                if (finalEmbeddings.get(i) == null) {
                    Embedding newEmbedding = newEmbeddings.get(newEmbeddingIndex++);
                    finalEmbeddings.set(i, newEmbedding);
                    // Lưu vào cache
                    cacheService.putToCache(textSegments.get(i).text(), newEmbedding);
                }
            }
        }
        
        // 3. Trả về kết quả tổng hợp
        return new Response<>(finalEmbeddings, new TokenUsage(inputTokenCount), null);
    }
}