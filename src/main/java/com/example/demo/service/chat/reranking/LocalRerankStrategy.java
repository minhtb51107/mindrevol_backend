package com.example.demo.service.chat.reranking;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.service.chat.integration.OpenAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalRerankStrategy {

    private final OpenAIService openAIService;

    public List<ChatMessage> rerank(String query, List<ChatMessage> messages, int topK) {
        if (messages.size() <= topK) {
            return messages;
        }

        try {
            // Sử dụng LLM để đánh giá relevance
            return rerankWithLLM(query, messages, topK);
        } catch (Exception e) {
            log.warn("LLM reranking failed, using heuristic fallback: {}", e.getMessage());
            return heuristicRerank(query, messages, topK);
        }
    }

    private List<ChatMessage> rerankWithLLM(String query, List<ChatMessage> messages, int topK) throws Exception {
        // Tạo prompt cho LLM re-ranking
        String prompt = buildRerankPrompt(query, messages);

        List<Map<String, String>> llmMessages = List.of(
            Map.of("role", "system", "content", "Bạn là trợ lý đánh giá mức độ liên quan của văn bản."),
            Map.of("role", "user", "content", prompt)
        );

        String response = openAIService.getChatCompletion(llmMessages, "gpt-3.5-turbo", 500);
        return parseLLMResponse(response, messages, topK);
    }

    private String buildRerankPrompt(String query, List<ChatMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Hãy đánh giá mức độ liên quan của các đoạn văn bản sau với câu hỏi: \"")
              .append(query).append("\"\n\n");

        prompt.append("Trả về danh sách các số thứ tự (từ 0 đến ").append(messages.size() - 1)
              .append(") theo thứ tự từ liên quan nhất đến ít liên quan nhất, mỗi số trên một dòng.\n\n");

        for (int i = 0; i < messages.size(); i++) {
            prompt.append("[").append(i).append("]: ").append(messages.get(i).getContent().substring(0, 
                Math.min(200, messages.get(i).getContent().length()))).append("\n");
        }

        return prompt.toString();
    }

    private List<ChatMessage> parseLLMResponse(String response, List<ChatMessage> messages, int topK) {
        // Parse response từ LLM (danh sách các index theo thứ tự)
        List<Integer> indices = response.lines()
            .map(String::trim)
            .filter(line -> line.matches("\\d+"))
            .map(Integer::parseInt)
            .filter(index -> index >= 0 && index < messages.size())
            .distinct()
            .limit(topK)
            .collect(Collectors.toList());

        return indices.stream()
            .map(messages::get)
            .collect(Collectors.toList());
    }

    private List<ChatMessage> heuristicRerank(String query, List<ChatMessage> messages, int topK) {
        // Fallback heuristic dựa trên multiple signals
        return messages.stream()
            .sorted(Comparator.comparingDouble((ChatMessage msg) -> 
                calculateRelevanceScore(msg, query)).reversed())
            .limit(topK)
            .collect(Collectors.toList());
    }

    private double calculateRelevanceScore(ChatMessage message, String query) {
        double score = 0.0;

        // Giữ nguyên similarity score từ vector search
        if (message.getSimilarityScore() != null) {
            score += message.getSimilarityScore() * 0.6;
        }

        // Keyword overlap
        score += calculateKeywordOverlap(message.getContent(), query) * 0.3;

        // Recency bonus
        score += calculateRecencyBonus(message) * 0.1;

        return score;
    }

    private double calculateKeywordOverlap(String content, String query) {
        if (content == null || query == null) return 0.0;
        
        String[] queryWords = query.toLowerCase().split("\\s+");
        String contentLower = content.toLowerCase();
        
        int matches = 0;
        for (String word : queryWords) {
            if (word.length() > 3 && contentLower.contains(word)) {
                matches++;
            }
        }
        
        return (double) matches / queryWords.length;
    }

    private double calculateRecencyBonus(ChatMessage message) {
        // Message trong 24h gần đây được bonus điểm
        long hoursDiff = java.time.Duration.between(
            message.getTimestamp(), java.time.LocalDateTime.now()
        ).toHours();
        
        return hoursDiff <= 24 ? 0.1 : 0.0;
    }
}