package com.example.demo.service.chat.reranking;

import com.example.demo.model.chat.ChatMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankingService {

    private final CohereRerankClient cohereRerankClient; // Hoặc LLM-based reranker
    private final LocalRerankStrategy localRerankStrategy;

    /**
     * Re-rank kết quả retrieval với mô hình re-ranking
     */
    public List<ChatMessage> rerankResults(String query, List<ChatMessage> retrievedMessages, int topK) {
        if (retrievedMessages == null || retrievedMessages.isEmpty() || retrievedMessages.size() <= topK) {
            return retrievedMessages;
        }

        try {
            // Ưu tiên dùng Cohere Rerank nếu có config
            if (cohereRerankClient.isAvailable()) {
                return cohereRerankClient.rerank(query, retrievedMessages, topK);
            }
            
            // Fallback: dùng local reranking strategy
            return localRerankStrategy.rerank(query, retrievedMessages, topK);
            
        } catch (Exception e) {
            log.warn("Re-ranking failed, using original order: {}", e.getMessage());
            return retrievedMessages.stream().limit(topK).collect(Collectors.toList());
        }
    }

    /**
     * Hybrid re-ranking kết hợp multiple signals
     */
    public List<ChatMessage> hybridRerank(String query, List<ChatMessage> messages, 
                                         Map<String, Double> weights, int topK) {
        if (messages.size() <= topK) {
            return messages;
        }

        // Tính điểm tổng hợp dựa trên multiple signals
        List<RerankScore> scoredMessages = messages.stream()
            .map(msg -> calculateHybridScore(msg, query, weights))
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());

        return scoredMessages.stream()
            .limit(topK)
            .map(RerankScore::getMessage)
            .collect(Collectors.toList());
    }

    private RerankScore calculateHybridScore(ChatMessage message, String query, Map<String, Double> weights) {
        double score = 0.0;
        
        // Semantic similarity (giữ nguyên từ vector search)
        score += message.getSimilarityScore() * weights.getOrDefault("semantic", 0.5);
        
        // Recency (ưu tiên message gần đây)
        score += calculateRecencyScore(message) * weights.getOrDefault("recency", 0.2);
        
        // Relevance to query (dựa trên keyword matching)
        score += calculateKeywordRelevance(message.getContent(), query) * weights.getOrDefault("keyword", 0.3);
        
        return new RerankScore(message, score);
    }

    private double calculateRecencyScore(ChatMessage message) {
        // Message càng mới thì điểm càng cao
        long hoursOld = java.time.Duration.between(
            message.getTimestamp(), java.time.LocalDateTime.now()
        ).toHours();
        
        return Math.max(0, 1.0 - (hoursOld / 168.0)); // Normalize trong 1 tuần
    }

    private double calculateKeywordRelevance(String content, String query) {
        if (content == null || query == null) return 0.0;
        
        String[] queryTerms = query.toLowerCase().split("\\s+");
        String contentLower = content.toLowerCase();
        
        int matches = 0;
        for (String term : queryTerms) {
            if (term.length() > 2 && contentLower.contains(term)) {
                matches++;
            }
        }
        
        return (double) matches / queryTerms.length;
    }

    @RequiredArgsConstructor
    @Getter
    private static class RerankScore {
        private final ChatMessage message;
        private final double score;
    }
}