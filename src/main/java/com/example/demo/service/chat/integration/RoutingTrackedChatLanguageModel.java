package com.example.demo.service.chat.integration;

import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoutingTrackedChatLanguageModel {

    private final ChatLanguageModel routingChatModel; // Sử dụng model dành riêng cho routing
    private final TokenUsageRepository tokenUsageRepository;
    private final OpenAiTokenizer tokenizer;

    // Giá của OpenAI cho gpt-3.5-turbo - Rẻ hơn nhiều
    private static final BigDecimal INPUT_TOKEN_PRICE = new BigDecimal("0.0000005"); // $0.50 / 1M tokens
    private static final BigDecimal OUTPUT_TOKEN_PRICE = new BigDecimal("0.0000015"); // $1.50 / 1M tokens

    @Autowired
    public RoutingTrackedChatLanguageModel(
            @Qualifier("routingChatModel") ChatLanguageModel routingChatModel, // ✅ Inject model routing
            TokenUsageRepository tokenUsageRepository) {
        this.routingChatModel = routingChatModel;
        this.tokenUsageRepository = tokenUsageRepository;
        this.tokenizer = new OpenAiTokenizer("gpt-3.5-turbo"); // Sử dụng tokenizer tương ứng
    }

    public Response<AiMessage> generate(List<ChatMessage> messages, Long userId, Long sessionId) {
        int inputTokenCount = tokenizer.estimateTokenCountInMessages(messages);

        // Gọi đến model routing
        Response<AiMessage> response = routingChatModel.generate(messages);
        
        dev.langchain4j.model.output.TokenUsage usageInfo = response.tokenUsage();
        int outputTokenCount = usageInfo.outputTokenCount();

        BigDecimal cost = calculateCost(inputTokenCount, outputTokenCount);

        TokenUsage tokenUsageRecord = TokenUsage.builder()
                .userId(userId)
                .sessionId(sessionId)
                .modelName("gpt-3.5-turbo-router") // Đặt tên riêng để dễ phân biệt
                .inputTokens(inputTokenCount)
                .outputTokens(outputTokenCount)
                .cost(cost)
                .timestamp(LocalDateTime.now())
                .build();
        tokenUsageRepository.save(tokenUsageRecord);

        return response;
    }
    
    private BigDecimal calculateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = INPUT_TOKEN_PRICE.multiply(new BigDecimal(inputTokens));
        BigDecimal outputCost = OUTPUT_TOKEN_PRICE.multiply(new BigDecimal(outputTokens));
        return inputCost.add(outputCost);
    }
}