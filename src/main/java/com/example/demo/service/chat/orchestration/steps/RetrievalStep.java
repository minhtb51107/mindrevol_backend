package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.orchestration.pipeline.result.RetrievalStepResult; // Import result class

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
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalStep implements PipelineStep<RetrievalStepResult>  {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    
    @Override
    public String getStepName() {
        return "retrieval";
    }

    @LogExecutionTime
    @Override
    public RetrievalStepResult execute(RagContext context) {
        String queryToEmbed = context.getTransformedQuery() != null ?
                              context.getTransformedQuery() :
                              context.getInitialQuery();

        log.debug("Executing retrieval with query: \"{}\"", queryToEmbed);

        Embedding queryEmbedding = embeddingModel.embed(queryToEmbed).content();

        Filter sessionMessageFilter = new And(
                new IsEqualTo("sessionId", context.getSession().getId().toString()),
                new IsEqualTo("docType", "message")
        );

        Filter userKnowledgeFilter = new And(
                new IsEqualTo("userId", context.getUser().getId().toString()),
                new IsEqualTo("docType", "knowledge")
        );
        
        Filter finalFilter = new Or(sessionMessageFilter, userKnowledgeFilter);

        if (context.getTempFileId() != null && !context.getTempFileId().isBlank()) {
            Filter tempFileFilter = new And(
                new And(
                    new IsEqualTo("docType", "temp_file"),
                    new IsEqualTo("sessionId", context.getSession().getId().toString())
                ),
                new IsEqualTo("tempFileId", context.getTempFileId())
            );
            finalFilter = new Or(finalFilter, tempFileFilter);
        }

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20)
                .filter(finalFilter)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);

        // Trả về đối tượng result thay vì thay đổi context
        return RetrievalStepResult.builder()
                .queryEmbedding(queryEmbedding)
                .metadataFilter(finalFilter)
                .retrievedMatches(searchResult.matches())
                .build();
    }
}