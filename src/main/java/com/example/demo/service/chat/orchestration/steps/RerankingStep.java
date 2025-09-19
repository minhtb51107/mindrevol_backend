package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.reranking.RerankingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RerankingStep implements RagStep {

    private final RerankingService rerankingService;

    // Di chuyển cache kiểm tra technical query vào đây
    private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @Override
    public RagContext execute(RagContext context) {
        String query = context.getInitialQuery();
        List<EmbeddingMatch<TextSegment>> initialMatches = context.getRetrievedMatches();

        List<EmbeddingMatch<TextSegment>> rerankedMatches;

        if (isTechnicalQuery(query)) {
            // ... logic rerank local
            Map<String, Double> weights = Map.of("semantic", 0.4, "recency", 0.3, "keyword", 0.3);
            rerankedMatches = rerankingService.hybridRerank(query, initialMatches, weights, 5);
        } else {
            // ... logic rerank Cohere
            rerankedMatches = rerankingService.rerankResults(query, initialMatches, 5);
        }

        // 1. Cập nhật các match đã được rerank
        context.setRerankedMatches(rerankedMatches);

        // 2. Tạo chuỗi context cho bước generate
        String ragContextString = rerankedMatches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n---\n"));
        context.setRagContextString(ragContextString);

        return context;
    }

    // Di chuyển logic private từ ChatAIService
    private boolean isTechnicalQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return technicalQueryCache.get(query, this::analyzeTechnicalQuery);
    }

    private boolean analyzeTechnicalQuery(String query) {
        String lowerQuery = query.toLowerCase();
        String[] technicalKeywords = {"java", "code", "api", "error", "exception", "debug", "sql"};
        for (String keyword : technicalKeywords) {
            if (lowerQuery.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}