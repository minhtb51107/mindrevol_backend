package com.example.demo.service.chat.reranking;

//import com.example.demo.model.chat.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CohereRerankClient {

    @Value("${cohere.api.key:}")
    private String cohereApiKey;

    @Value("${cohere.rerank.model:rerank-multilingual-v3.0}")
    private String rerankModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isAvailable() {
        return cohereApiKey != null && !cohereApiKey.isBlank();
    }

    public List<EmbeddingMatch<TextSegment>> rerank(String query, List<EmbeddingMatch<TextSegment>> documents, int topK) {
        if (!isAvailable()) {
            throw new IllegalStateException("Cohere API key not configured");
        }
        if (documents == null || documents.isEmpty() || documents.size() <= topK) {
            return documents;
        }

        try {
            // Chuẩn bị request body
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", rerankModel,
                "query", query,
                "documents", documents.stream()
                    .map(match -> match.embedded().text()) // ✅ Lấy text từ TextSegment
                    .collect(Collectors.toList()),
                "top_n", Math.min(topK * 2, documents.size())
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.cohere.ai/v1/rerank"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cohereApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Cohere API error: " + response.body());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode results = responseJson.get("results");

            // Map kết quả re-rank trở lại ChatMessage
            return results.findValues("index").stream()
                .map(JsonNode::asInt)
                .limit(topK)
                .map(index -> documents.get(index))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Cohere reranking failed: {}", e.getMessage());
            throw new RuntimeException("Re-ranking failed", e);
        }
    }
}