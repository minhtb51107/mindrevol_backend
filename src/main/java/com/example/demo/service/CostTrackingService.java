package com.example.demo.service;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.ExternalServiceUsage;
import com.example.demo.model.monitoring.TokenUsage; // ✅ 1. THAY ĐỔI IMPORT
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.ExternalServiceUsageRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository; // ✅ 1. THÊM IMPORT
import com.example.demo.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CostTrackingService {

    // --- Giữ lại repository cũ cho phương thức recordUsage ---
    private final ExternalServiceUsageRepository externalServiceUsageRepository;
    
    // ✅ 2. THÊM REPOSITORY MỚI CHO VIỆC TRACKING TOKEN
    private final TokenUsageRepository tokenUsageRepository; 
    
    private final UserRepository userRepository;

    // --- Các hằng số giá tiền ---
    private static final BigDecimal GPT4O_INPUT_PRICE_PER_1M = new BigDecimal("5.00");
    private static final BigDecimal GPT4O_OUTPUT_PRICE_PER_1M = new BigDecimal("15.00");
    private static final BigDecimal GPT35_INPUT_PRICE_PER_1M = new BigDecimal("0.50");
    private static final BigDecimal GPT35_OUTPUT_PRICE_PER_1M = new BigDecimal("1.50");
    
    
    /**
     * ✅ 3. PHƯƠNG THỨC MỚI ĐỂ GHI LẠI CHI PHÍ TOKEN CHI TIẾT
     * Ghi lại chi phí sử dụng token từ các lệnh gọi LLM một cách bất đồng bộ.
     *
     * @param inputTokens      Số lượng token đầu vào.
     * @param outputTokens     Số lượng token đầu ra.
     * @param modelName        Tên của model được sử dụng.
     * @param callIdentifier   Định danh cho lệnh gọi LLM (ví dụ: "query_rewrite", "generation").
     */
    @Async("taskExecutor") // Sử dụng Aysnc Executor nếu bạn đã cấu hình
    public void addTokenUsageCost(Integer inputTokens, String modelName, Integer outputTokens, String callIdentifier) {
        if (inputTokens == null || outputTokens == null) {
            return;
        }

        User currentUser = UserUtils.getCurrentUser(userRepository);
        if (currentUser == null) {
            return; // Không ghi lại nếu không có người dùng
        }

        BigDecimal cost = calculateCost(modelName, inputTokens, outputTokens);
        int totalTokens = inputTokens + outputTokens;

        TokenUsage usageRecord = new TokenUsage();
        usageRecord.setUser(currentUser);
        usageRecord.setModelName(modelName);
        usageRecord.setInputTokens(inputTokens);
        usageRecord.setOutputTokens(outputTokens);
        usageRecord.setTotalTokens(totalTokens);
        usageRecord.setCost(cost);
        usageRecord.setCreatedAt(LocalDateTime.now());
        usageRecord.setCallIdentifier(callIdentifier); // LƯU ĐỊNH DANH

        tokenUsageRepository.save(usageRecord);
    }

    /**
     * Phương thức cũ để ghi lại việc sử dụng dịch vụ bên ngoài nói chung.
     * (Giữ lại để đảm bảo tương thích ngược)
     */
    public void recordUsage(String serviceName, String usageUnit, Long usageAmount, BigDecimal cost) {
        User currentUser = UserUtils.getCurrentUser(userRepository);
        if (currentUser == null) {
            return;
        }

        ExternalServiceUsage usage = new ExternalServiceUsage();
        usage.setServiceName(serviceName);
        usage.setUsageUnit(usageUnit);
        usage.setUsageAmount(usageAmount);
        usage.setCost(cost);
        usage.setUser(currentUser);
        usage.setCreatedAt(LocalDateTime.now());
        
        externalServiceUsageRepository.save(usage);
    }
    
    /**
     * Hàm helper để tính toán chi phí.
     */
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