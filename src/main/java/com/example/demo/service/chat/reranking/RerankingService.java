package com.example.demo.service.chat.reranking;

import com.example.demo.service.CostTrackingService; // ✅ 1. IMPORT SERVICE THEO DÕI CHI PHÍ
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RerankingService {

    private final CohereRerankClient cohereRerankClient;
    private final LocalRerankStrategy localRerankStrategy;
    private final CostTrackingService costTrackingService; // ✅ 2. INJECT SERVICE VÀO CONSTRUCTOR

    // ✅ 3. THÊM HẰNG SỐ ĐỊNH NGHĨA GIÁ
    // LƯU Ý: Đây là giá tham khảo. Bạn cần kiểm tra lại trên trang giá của Cohere
    // và điều chỉnh cho phù hợp với model và đơn vị tính của họ (ví dụ: trên 1000 documents, v.v.)
    private static final BigDecimal COHERE_COST_PER_1000_DOCS = new BigDecimal("1.00"); // $1.00 / 1000 documents

    /**
     * Re-rank kết quả retrieval với mô hình re-ranking (Cohere)
     */
    public List<EmbeddingMatch<TextSegment>> rerankResults(String query, List<EmbeddingMatch<TextSegment>> retrievedMessages, int topK) {
        if (retrievedMessages == null || retrievedMessages.isEmpty() || retrievedMessages.size() <= topK) {
            return retrievedMessages;
        }

        try {
            if (cohereRerankClient.isAvailable()) {
                
                // --- BẮT ĐẦU TÍCH HỢP THEO DÕI CHI PHÍ ---
                long documentCount = retrievedMessages.size();
                
                // Gọi API Cohere trước
                List<EmbeddingMatch<TextSegment>> rerankedResults = cohereRerankClient.rerank(query, retrievedMessages, topK);

                // Chỉ ghi nhận chi phí KHI gọi API thành công
                BigDecimal estimatedCost = new BigDecimal(documentCount)
                        .divide(new BigDecimal(1000))
                        .multiply(COHERE_COST_PER_1000_DOCS);

                costTrackingService.recordUsage("COHERE_RERANK", "documents", documentCount, estimatedCost);
                log.info("Successfully reranked {} documents with Cohere. Estimated cost: ${}", documentCount, estimatedCost);
                // --- KẾT THÚC TÍCH HỢP ---

                return rerankedResults;
            }
            
            log.debug("Cohere client not available, falling back to local hybrid rerank.");
            return fallbackToHybrid(query, retrievedMessages, topK);

        } catch (Exception e) {
            log.warn("Cohere API re-ranking failed: {}. Falling back to local hybrid rerank.", e.getMessage());
            // Không ghi nhận chi phí ở đây vì API đã thất bại
            return fallbackToHybrid(query, retrievedMessages, topK);
        }
    }

    // --- CÁC PHƯƠNG THỨC CÒN LẠI GIỮ NGUYÊN ---

    /**
     * Phương thức helper mới để gọi local hybrid rerank với trọng số mặc định.
     */
    private List<EmbeddingMatch<TextSegment>> fallbackToHybrid(String query, List<EmbeddingMatch<TextSegment>> messages, int topK) {
        Map<String, Double> defaultWeights = Map.of(
            "semantic", 0.5,
            "recency", 0.2,
            "keyword", 0.3
        );
        return hybridRerank(query, messages, defaultWeights, topK);
    }

    /**
     * Hybrid re-ranking kết hợp multiple signals
     */
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

    private RerankScore calculateHybridScore(EmbeddingMatch<TextSegment> match, String query, Map<String, Double> weights) {
        double score = 0.0;
        TextSegment segment = match.embedded();
        score += match.score() * weights.getOrDefault("semantic", 0.5);
        score += calculateRecencyScore(segment) * weights.getOrDefault("recency", 0.2);
        score += calculateKeywordRelevance(segment.text(), query) * weights.getOrDefault("keyword", 0.3);
        return new RerankScore(match, score);
    }

    private double calculateRecencyScore(TextSegment segment) {
        try {
            String timestampStr = segment.metadata().getString("messageTimestamp");
            if (timestampStr == null) return 0.0;
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr);
            long hoursOld = java.time.Duration.between(timestamp, java.time.LocalDateTime.now()).toHours();
            return Math.max(0, 1.0 - (hoursOld / 168.0));
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
        private final EmbeddingMatch<TextSegment> message;
        private final double score;
    }
}