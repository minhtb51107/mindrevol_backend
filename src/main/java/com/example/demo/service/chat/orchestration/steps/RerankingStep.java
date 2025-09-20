package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.reranking.RerankingService;
import com.example.demo.service.chat.context.ContextCompressionService; // ✅ THÊM IMPORT
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
    private final ContextCompressionService contextCompressionService; // ✅ INJECT SERVICE

    private final Cache<String, Boolean> technicalQueryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    @Override
    public RagContext execute(RagContext context) {
        String query = context.getInitialQuery();
        List<EmbeddingMatch<TextSegment>> initialMatches = context.getRetrievedMatches();
        
        if (initialMatches == null || initialMatches.isEmpty()) {
            context.setRagContextString(""); // Đảm bảo ngữ cảnh trống nếu không có kết quả
            return context;
        }

        List<EmbeddingMatch<TextSegment>> rerankedMatches;

        if (isTechnicalQuery(query)) {
            Map<String, Double> weights = Map.of("semantic", 0.4, "recency", 0.3, "keyword", 0.3);
            rerankedMatches = rerankingService.hybridRerank(query, initialMatches, weights, 5);
        } else {
            rerankedMatches = rerankingService.rerankResults(query, initialMatches, 5);
        }

        // 1. Cập nhật các kết quả đã được rerank vào context
        context.setRerankedMatches(rerankedMatches);

        // ✅ BƯỚC 2: CẢI TIẾN - NÉN NGỮ CẢNH TRƯỚC KHI TẠO PROMPT
        // Trích xuất danh sách TextSegment từ các kết quả đã rerank
        List<TextSegment> documentsToCompress = rerankedMatches.stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());

        // Sử dụng service để nén hoặc nối chuỗi các văn bản một cách thông minh
        String finalRagContext = contextCompressionService.compressDocumentContext(documentsToCompress, query);

        // Cập nhật chuỗi ngữ cảnh đã được xử lý vào RagContext
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