package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.orchestration.pipeline.result.RetrievalStepResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalStep implements PipelineStep<RetrievalStepResult> {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Thêm @Value để có thể cấu hình từ application.yml
    @Value("${retrieval.max-results:50}")
    private int maxResults;

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

        log.debug("Executing hybrid retrieval with query: \"{}\"", queryToEmbed);

        Embedding queryEmbedding = embeddingModel.embed(queryToEmbed).content();
        Filter finalFilter = buildFilter(context);

        // 1. Vector Search (Semantic) - SỬ DỤNG GIÁ TRỊ CẤU HÌNH
        EmbeddingSearchRequest vectorRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults) // ✅ THAY ĐỔI TỪ 15
                .filter(finalFilter)
                .build();
        EmbeddingSearchResult<TextSegment> vectorSearchResult = embeddingStore.search(vectorRequest);
        log.info("Vector search found {} results.", vectorSearchResult.matches().size());

        // 2. Keyword Search (Full-Text) - SỬ DỤNG GIÁ TRỊ CẤU HÌNH
        List<EmbeddingMatch<TextSegment>> keywordMatches = searchByKeyword(queryToEmbed, finalFilter, maxResults); // ✅ THAY ĐỔI TỪ 15
        log.info("Keyword search found {} results.", keywordMatches.size());

        // ... (phần còn lại của phương thức không đổi)
        // 3. Kết hợp, loại bỏ trùng lặp và xếp hạng lại
        List<EmbeddingMatch<TextSegment>> combinedMatches = Stream.concat(
                        vectorSearchResult.matches().stream(),
                        keywordMatches.stream()
                )
                .collect(Collectors.toMap(
                        EmbeddingMatch::embeddingId,
                        match -> match,
                        (match1, match2) -> match1.score() >= match2.score() ? match1 : match2
                ))
                .values().stream()
                .sorted(Comparator.comparingDouble((EmbeddingMatch<?> match) -> match.score()).reversed())
                .collect(Collectors.toList());

        log.info("Combined and deduplicated search resulted in {} matches.", combinedMatches.size());

        return RetrievalStepResult.builder()
                .queryEmbedding(queryEmbedding)
                .metadataFilter(finalFilter)
                .retrievedMatches(combinedMatches)
                .build();
    }

    private List<EmbeddingMatch<TextSegment>> searchByKeyword(String query, Filter filter, int maxResults) {
        try {
            String tsQuery = Arrays.stream(query.split("\\s+"))
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.joining(" & "));

            FilterToSqlResult filterToSqlResult = convertFilterToSql(filter);
            String whereClause = filterToSqlResult.getSql();
            List<Object> params = new ArrayList<>(filterToSqlResult.getParams());

            String sql = "SELECT id, embedding, text_segment, metadata, ts_rank(text_segment_tsv, to_tsquery('simple', ?)) as score " +
                         "FROM message_embeddings " +
                         "WHERE text_segment_tsv @@ to_tsquery('simple', ?) " +
                         (!whereClause.isEmpty() ? "AND " + whereClause + " " : "") +
                         "ORDER BY score DESC LIMIT ?";

            List<Object> queryParams = new ArrayList<>();
            queryParams.add(tsQuery);
            queryParams.add(tsQuery);
            queryParams.addAll(params);
            queryParams.add(maxResults);

            return jdbcTemplate.query(sql, queryParams.toArray(), embeddingMatchRowMapper());
        } catch (Exception e) {
            log.error("Error during keyword search", e);
            return Collections.emptyList();
        }
    }

    private RowMapper<EmbeddingMatch<TextSegment>> embeddingMatchRowMapper() {
        return (rs, rowNum) -> {
            try {
                double score = rs.getDouble("score");
                String embeddingId = rs.getString("id");
                String text = rs.getString("text_segment");
                String metadataJson = rs.getString("metadata");

                Map<String, Object> metadataMap = objectMapper.readValue(metadataJson, new TypeReference<>() {});
                Metadata metadata = new Metadata(metadataMap);

                String embeddingStr = rs.getString("embedding").replace("[", "").replace("]", "");
                List<Float> vector = Arrays.stream(embeddingStr.split(","))
                                           .map(Float::parseFloat)
                                           .collect(Collectors.toList());
                Embedding embedding = Embedding.from(vector);

                TextSegment textSegment = TextSegment.from(text, metadata);

                return new EmbeddingMatch<>(score, embeddingId, embedding, textSegment);

            } catch (Exception e) {
                log.error("Failed to map row to EmbeddingMatch", e);
                return null;
            }
        };
    }

    private static class FilterToSqlResult {
        private final String sql;
        private final List<Object> params;

        public FilterToSqlResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        public String getSql() { return sql; }
        public List<Object> getParams() { return params; }
    }
    
    private FilterToSqlResult convertFilterToSql(Filter filter) {
        if (filter instanceof IsEqualTo) {
            IsEqualTo isEqualTo = (IsEqualTo) filter;
            return new FilterToSqlResult(String.format("(metadata ->> '%s') = ?", isEqualTo.key()), Collections.singletonList(isEqualTo.comparisonValue()));
        } else if (filter instanceof And) {
            And and = (And) filter;
            FilterToSqlResult left = convertFilterToSql(and.left());
            FilterToSqlResult right = convertFilterToSql(and.right());
            String sql = String.format("(%s AND %s)", left.getSql(), right.getSql());
            List<Object> params = new ArrayList<>(left.getParams());
            params.addAll(right.getParams());
            return new FilterToSqlResult(sql, params);
        } else if (filter instanceof Or) {
            Or or = (Or) filter;
            FilterToSqlResult left = convertFilterToSql(or.left());
            FilterToSqlResult right = convertFilterToSql(or.right());
            String sql = String.format("(%s OR %s)", left.getSql(), right.getSql());
            List<Object> params = new ArrayList<>(left.getParams());
            params.addAll(right.getParams());
            return new FilterToSqlResult(sql, params);
        }
        return new FilterToSqlResult("", Collections.emptyList());
    }

    private Filter buildFilter(RagContext context) {
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
        return finalFilter;
    }
}