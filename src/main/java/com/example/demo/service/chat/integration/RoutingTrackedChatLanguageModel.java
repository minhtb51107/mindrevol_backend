package com.example.demo.service.chat.integration;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
// Xóa @Service và các import không cần thiết khác
// import org.springframework.stereotype.Service; 

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Sửa đổi 1: Không còn là @Service, mà là một class kế thừa từ class tracking chung
public class RoutingTrackedChatLanguageModel extends TrackedChatLanguageModel {

    // Sửa đổi 2: Constructor nhận đầy đủ tham số và gọi lên class cha
    public RoutingTrackedChatLanguageModel(
            ChatLanguageModel delegate, 
            String modelName, 
            TokenUsageRepository tokenUsageRepository, 
            UserRepository userRepository) {
        super(delegate, modelName, tokenUsageRepository, userRepository);
    }

    // Sửa đổi 3: Ghi đè (Override) phương thức saveTokenUsage
    // Mục đích: Giữ lại tính năng hữu ích là thêm hậu tố "-router" vào tên model để dễ phân biệt
    @Override
    protected void saveTokenUsage(dev.langchain4j.model.output.TokenUsage usageInfo, String modelName, BigDecimal cost, User user) {
        if (user == null) {
            return;
        }

        TokenUsage usageRecord = new TokenUsage();
        
        // Ghi đè logic: Thêm "-router" vào tên model
        usageRecord.setModelName(modelName + "-router"); 
        
        // Logic còn lại giống hệt class cha
        usageRecord.setInputTokens(usageInfo.inputTokenCount());
        usageRecord.setOutputTokens(usageInfo.outputTokenCount());
        usageRecord.setTotalTokens(usageInfo.totalTokenCount());
        usageRecord.setCost(cost);
        usageRecord.setUser(user);
        usageRecord.setCreatedAt(LocalDateTime.now());
        
        // Sử dụng tokenUsageRepository đã được kế thừa từ class cha
        tokenUsageRepository.save(usageRecord);
    }
}