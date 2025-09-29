package com.example.demo.service.chat.integration;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import com.example.demo.util.UserUtils;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TrackedChatLanguageModel implements ChatLanguageModel {

    protected final ChatLanguageModel delegate;
    protected final TokenUsageRepository tokenUsageRepository;
    protected final UserRepository userRepository;
    private final String modelName;

    // ... (các hằng số giá tiền giữ nguyên) ...
    private static final BigDecimal GPT4O_INPUT_PRICE_PER_1M = new BigDecimal("5.00");
    private static final BigDecimal GPT4O_OUTPUT_PRICE_PER_1M = new BigDecimal("15.00");
    private static final BigDecimal GPT35_INPUT_PRICE_PER_1M = new BigDecimal("0.50");
    private static final BigDecimal GPT35_OUTPUT_PRICE_PER_1M = new BigDecimal("1.50");

    public TrackedChatLanguageModel(ChatLanguageModel delegate, String modelName, TokenUsageRepository tokenUsageRepository, UserRepository userRepository) {
        this.delegate = delegate;
        this.modelName = modelName;
        this.tokenUsageRepository = tokenUsageRepository;
        this.userRepository = userRepository;
    }

    // --- CÁC PHƯƠNG THỨC GỐC (ĐỂ TƯƠNG THÍCH NGƯỢC) ---

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        // Gọi phương thức mới với callIdentifier mặc định
        return generate(messages, "default_generation");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        // Gọi phương thức mới với callIdentifier mặc định
        return generate(messages, toolSpecifications, "default_tool_generation");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        // Gọi phương thức mới với callIdentifier mặc định
        return generate(messages, toolSpecification, "default_tool_generation");
    }


    // --- CÁC PHƯƠNG THỨC MỚI VỚI CALL IDENTIFIER ---

    public Response<AiMessage> generate(List<ChatMessage> messages, String callIdentifier) {
        Response<AiMessage> response = delegate.generate(messages);
        trackUsage(response, callIdentifier); // Truyền callIdentifier vào
        return response;
    }

    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, String callIdentifier) {
        Response<AiMessage> response = delegate.generate(messages, toolSpecifications);
        trackUsage(response, callIdentifier); // Truyền callIdentifier vào
        return response;
    }

    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification, String callIdentifier) {
        Response<AiMessage> response = delegate.generate(messages, toolSpecification);
        trackUsage(response, callIdentifier); // Truyền callIdentifier vào
        return response;
    }


    // --- HELPER METHODS (ĐÃ CẬP NHẬT) ---

    private void trackUsage(Response<AiMessage> response, String callIdentifier) {
        dev.langchain4j.model.output.TokenUsage usageInfo = response.tokenUsage();
        if (usageInfo != null) {
            BigDecimal cost = calculateCost(this.modelName, usageInfo.inputTokenCount(), usageInfo.outputTokenCount());
            User currentUser = UserUtils.getCurrentUser(userRepository);
            // Truyền callIdentifier xuống hàm save
            saveTokenUsage(usageInfo, this.modelName, cost, currentUser, callIdentifier);
        }
    }

    protected void saveTokenUsage(dev.langchain4j.model.output.TokenUsage usageInfo, String modelName, BigDecimal cost, User user, String callIdentifier) {
        if (user == null) return;
        TokenUsage usageRecord = new TokenUsage();
        usageRecord.setModelName(modelName);
        usageRecord.setInputTokens(usageInfo.inputTokenCount());
        usageRecord.setOutputTokens(usageInfo.outputTokenCount());
        usageRecord.setTotalTokens(usageInfo.totalTokenCount());
        usageRecord.setCost(cost);
        usageRecord.setUser(user);
        usageRecord.setCreatedAt(LocalDateTime.now());
        usageRecord.setCallIdentifier(callIdentifier); // ✅ LƯU CALL IDENTIFIER
        tokenUsageRepository.save(usageRecord);
    }

    private BigDecimal calculateCost(String modelName, int inputTokens, int outputTokens) {
        BigDecimal inputPrice;
        BigDecimal outputPrice;

        if (modelName != null && (modelName.contains("gpt-4") || modelName.contains("gpt-4o"))) {
            inputPrice = GPT4O_INPUT_PRICE_PER_1M;
            outputPrice = GPT4O_OUTPUT_PRICE_PER_1M;
        } else {
            inputPrice = GPT35_INPUT_PRICE_PER_1M;
            outputPrice = GPT35_OUTPUT_PRICE_PER_1M;
        }

        BigDecimal inputCost = inputPrice.multiply(new BigDecimal(inputTokens)).divide(new BigDecimal(1_000_000));
        BigDecimal outputCost = outputPrice.multiply(new BigDecimal(outputTokens)).divide(new BigDecimal(1_000_000));

        return inputCost.add(outputCost);
    }
}