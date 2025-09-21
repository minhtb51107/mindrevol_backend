package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.context.ContextCompressionService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep; // ✅ THAY ĐỔI 1: Import đúng interface
import com.example.demo.service.chat.reranking.RerankingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RerankingStep implements PipelineStep { // ✅ THAY ĐỔI 2: Implement đúng interface

    private final RerankingService rerankingService;
    private final ContextCompressionService contextCompressionService;

    private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // ✅ THAY ĐỔI 3: Thêm phương thức getStepName()
    @Override
    public String getStepName() {
        return "reranking"; // Tên này phải khớp với trong application.yml
    }

    // ✅ THAY ĐỔI 4: Đổi tên phương thức từ execute thành process
    @Override
    @LogExecutionTime // Thêm annotation đo lường
    public RagContext execute(RagContext context) {
        String query = context.getInitialQuery();
        List<EmbeddingMatch<TextSegment>> initialMatches = context.getRetrievedMatches();

        if (initialMatches == null || initialMatches.isEmpty()) {
            context.setRagContextString("");
            return context;
        }

        List<EmbeddingMatch<TextSegment>> rerankedMatches;

        if (isTechnicalQuery(query)) {
            Map<String, Double> weights = Map.of("semantic", 0.4, "recency", 0.3, "keyword", 0.3);
            rerankedMatches = rerankingService.hybridRerank(query, initialMatches, weights, 5);
        } else {
            rerankedMatches = rerankingService.rerankResults(query, initialMatches, 5);
        }

        context.setRerankedMatches(rerankedMatches);

        List<TextSegment> documentsToCompress = rerankedMatches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());

        String finalRagContext = contextCompressionService.compressDocumentContext(documentsToCompress, query);

        context.setRagContextString(finalRagContext);

        return context;
    }

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