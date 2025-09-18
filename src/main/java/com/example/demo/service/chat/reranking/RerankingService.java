package com.example.demo.service.chat.reranking;

//import com.example.demo.model.chat.ChatMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.ChatMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankingService {

    private final CohereRerankClient cohereRerankClient; // Hoặc LLM-based reranker
    private final LocalRerankStrategy localRerankStrategy;

    /**
     * Re-rank kết quả retrieval với mô hình re-ranking
     */
    // ✅ THAY ĐỔI SIGNATURE:
 // Trong file: demo-2/src/main/java/com/example/demo/service/chat/reranking/RerankingService.java

    /**
     * Re-rank kết quả retrieval với mô hình re-ranking (Cohere)
     */
    // ✅ THAY THẾ PHƯƠNG THỨC NÀY
    public List<EmbeddingMatch<TextSegment>> rerankResults(String query, List<EmbeddingMatch<TextSegment>> retrievedMessages, int topK) {
        if (retrievedMessages == null || retrievedMessages.isEmpty() || retrievedMessages.size() <= topK) {
            return retrievedMessages;
        }

        try {
            if (cohereRerankClient.isAvailable()) {
                // Thử chạy API Cohere (ưu tiên)
                return cohereRerankClient.rerank(query, retrievedMessages, topK);
            }
            
            // Nếu Cohere không được cấu hình, chạy fallback ngay lập tức
            log.debug("Cohere client not available, falling back to local hybrid rerank.");
            return fallbackToHybrid(query, retrievedMessages, topK);

        } catch (Exception e) {
            // ✅ SỬA LỖI FALLBACK: 
            // Nếu Cohere API bị lỗi (ví dụ: 500, timeout), chúng ta chạy fallback 
            // logic hybrid rerank (local) thay vì trả về kết quả thô.
            log.warn("Cohere API re-ranking failed: {}. Falling back to local hybrid rerank.", e.getMessage());
            return fallbackToHybrid(query, retrievedMessages, topK);
        }
    }

    /**
     * Phương thức helper mới để gọi local hybrid rerank với trọng số mặc định.
     */
    private List<EmbeddingMatch<TextSegment>> fallbackToHybrid(String query, List<EmbeddingMatch<TextSegment>> messages, int topK) {
        // Sử dụng trọng số mặc định (an toàn) khi Cohere thất bại
        Map<String, Double> defaultWeights = Map.of(
            "semantic", 0.5, // Điểm semantic từ vector search
            "recency", 0.2,  // Độ mới
            "keyword", 0.3   // Từ khóa
        );
        
        // Gọi logic hybrid local của bạn
        return hybridRerank(query, messages, defaultWeights, topK);
    }

    /**
     * Hybrid re-ranking kết hợp multiple signals
     */
    // ✅ THAY ĐỔI SIGNATURE:
    public List<EmbeddingMatch<TextSegment>> hybridRerank(String query, List<EmbeddingMatch<TextSegment>> messages, 
                                         Map<String, Double> weights, int topK) {
        if (messages.size() <= topK) {
            return messages;
        }

        List<RerankScore> scoredMessages = messages.stream()
            .map(msg -> calculateHybridScore(msg, query, weights))
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .collect(Collectors.toList());

        return scoredMessages.stream()
            .limit(topK)
            .map(RerankScore::getMessage)
            .collect(Collectors.toList());
    }

    // ✅ THAY ĐỔI SIGNATURE:
    private RerankScore calculateHybridScore(EmbeddingMatch<TextSegment> match, String query, Map<String, Double> weights) {
        double score = 0.0;
        TextSegment segment = match.embedded();

        // 1. Semantic similarity (LẤY TRỰC TIẾP TỪ MATCH)
        score += match.score() * weights.getOrDefault("semantic", 0.5);
        
        // 2. Recency (LẤY TỪ METADATA)
        score += calculateRecencyScore(segment) * weights.getOrDefault("recency", 0.2);
        
        // 3. Relevance (LẤY TỪ NỘI DUNG)
        score += calculateKeywordRelevance(segment.text(), query) * weights.getOrDefault("keyword", 0.3);
        
        return new RerankScore(match, score);
    }

    // ✅ THAY ĐỔI SIGNATURE:
    private double calculateRecencyScore(TextSegment segment) {
        try {
            // Giả sử bạn lưu timestamp trong metadata khi ingest
            String timestampStr = segment.metadata().getString("messageTimestamp");
            if (timestampStr == null) return 0.0;
            
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr);
            long hoursOld = java.time.Duration.between(
                timestamp, java.time.LocalDateTime.now()
            ).toHours();
            
            return Math.max(0, 1.0 - (hoursOld / 168.0)); // Normalize trong 1 tuần
        } catch (DateTimeParseException e) {
            log.warn("Invalid timestamp in metadata: {}", e.getMessage());
            return 0.0;
        }
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
        // ✅ THAY ĐỔI TYPE:
        private final EmbeddingMatch<TextSegment> message;
        private final double score;
    }
}