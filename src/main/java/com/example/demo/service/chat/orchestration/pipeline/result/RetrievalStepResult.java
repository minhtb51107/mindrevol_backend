package com.example.demo.service.chat.orchestration.pipeline.result;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RetrievalStepResult {
    Embedding queryEmbedding;
    Filter metadataFilter;
    List<EmbeddingMatch<TextSegment>> retrievedMatches;
}