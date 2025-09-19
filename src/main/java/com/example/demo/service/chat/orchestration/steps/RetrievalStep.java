package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetrievalStep implements RagStep {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public RagContext execute(RagContext context) {
        // 1. Nhúng câu truy vấn
        Embedding queryEmbedding = embeddingModel.embed(context.getInitialQuery()).content();
        context.setQueryEmbedding(queryEmbedding);

        // 2. Xây dựng bộ lọc Metadata
        Filter sessionMessageFilter = new IsEqualTo("sessionId", context.getSession().getId().toString());
        Filter userKnowledgeFilter = new And(
                new IsEqualTo("userId", context.getUser().getId().toString()),
                new IsEqualTo("docType", "knowledge")
        );
        Filter finalFilter = new Or(sessionMessageFilter, userKnowledgeFilter);
        context.setMetadataFilter(finalFilter);

        // 3. Tạo yêu cầu tìm kiếm
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20) // Lấy nhiều hơn để Rerank
                .filter(finalFilter)
                .build();

        // 4. Thực thi tìm kiếm
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
        context.setRetrievedMatches(searchResult.matches());

        return context;
    }
}