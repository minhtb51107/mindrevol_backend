package com.example.demo.service.chat.integration;

import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrackedChatLanguageModel {

    private final ChatLanguageModel chatLanguageModel;
    private final TokenUsageRepository tokenUsageRepository;
    private final OpenAiTokenizer tokenizer;

    private static final BigDecimal INPUT_TOKEN_PRICE = new BigDecimal("0.000005"); // $5.00 / 1M tokens
    private static final BigDecimal OUTPUT_TOKEN_PRICE = new BigDecimal("0.000015"); // $15.00 / 1M tokens

    @Autowired
    public TrackedChatLanguageModel(ChatLanguageModel chatLanguageModel, TokenUsageRepository tokenUsageRepository) {
        this.chatLanguageModel = chatLanguageModel;
        this.tokenUsageRepository = tokenUsageRepository;
        this.tokenizer = new OpenAiTokenizer("gpt-4o");
    }

    public Response<AiMessage> generate(List<ChatMessage> messages, Long userId, Long sessionId) {
        int inputTokenCount = tokenizer.estimateTokenCountInMessages(messages);

        Response<AiMessage> response = chatLanguageModel.generate(messages);

        // ✅ SỬA LỖI: Sử dụng tên đầy đủ để tránh nhầm lẫn class
        // Khai báo rõ ràng là chúng ta muốn dùng class TokenUsage từ thư viện langchain4j
        dev.langchain4j.model.output.TokenUsage usageInfo = response.tokenUsage();

        // Bây giờ Java sẽ hiểu và không báo lỗi nữa
        int outputTokenCount = usageInfo.outputTokenCount();

        BigDecimal cost = calculateCost(inputTokenCount, outputTokenCount);

        // Tạo bản ghi để lưu vào DB (sử dụng class TokenUsage của bạn)
        TokenUsage tokenUsageRecord = TokenUsage.builder()
                .userId(userId)
                .sessionId(sessionId)
                .modelName("gpt-4o")
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