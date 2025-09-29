package com.example.demo.service.chat.integration;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RoutingTrackedChatLanguageModel extends TrackedChatLanguageModel {

    public RoutingTrackedChatLanguageModel(
            ChatLanguageModel delegate,
            String modelName,
            TokenUsageRepository tokenUsageRepository,
            UserRepository userRepository) {
        super(delegate, modelName, tokenUsageRepository, userRepository);
    }

    /**
     * ✅ ĐÃ CẬP NHẬT: Ghi đè phương thức mới với tham số `callIdentifier`.
     */
    @Override
    protected void saveTokenUsage(
            dev.langchain4j.model.output.TokenUsage usageInfo,
            String modelName,
            BigDecimal cost,
            User user,
            String callIdentifier // Thêm tham số mới để khớp với lớp cha
    ) {
        if (user == null) {
            return;
        }

        TokenUsage usageRecord = new TokenUsage();

        // Giữ lại logic cũ: Thêm "-router" vào tên model
        usageRecord.setModelName(modelName + "-router");

        // Logic còn lại giống hệt class cha
        usageRecord.setInputTokens(usageInfo.inputTokenCount());
        usageRecord.setOutputTokens(usageInfo.outputTokenCount());
        usageRecord.setTotalTokens(usageInfo.totalTokenCount());
        usageRecord.setCost(cost);
        usageRecord.setUser(user);
        usageRecord.setCreatedAt(LocalDateTime.now());
        
        // Thêm callIdentifier vào bản ghi
        usageRecord.setCallIdentifier(callIdentifier);

        tokenUsageRepository.save(usageRecord);
    }
}