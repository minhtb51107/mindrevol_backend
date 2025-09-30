package com.example.demo.service.chat.integration;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.TokenUsage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import com.example.demo.util.UserUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đây là một Decorator "bọc" một EmbeddingModel gốc.
 * Mục đích: chặn các lệnh gọi embedding, lấy thông tin token usage,
 * và ghi lại chi phí vào cơ sở dữ liệu.
 */

@Slf4j
public class TrackedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final TokenUsageRepository tokenUsageRepository;
    private final UserRepository userRepository;
    private final String modelName;

    // Giá tham khảo cho model text-embedding-3-small (chi phí trên 1 triệu token)
    private static final BigDecimal COST_PER_1M_TOKENS = new BigDecimal("0.02");

    public TrackedEmbeddingModel(EmbeddingModel delegate, String modelName, TokenUsageRepository tokenUsageRepository, UserRepository userRepository) {
        this.delegate = delegate;
        this.modelName = modelName;
        this.tokenUsageRepository = tokenUsageRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        // Gọi đến model embedding gốc để thực hiện công việc
        Response<List<Embedding>> response = delegate.embedAll(textSegments);
        
        // Lấy thông tin token từ response của LangChain4j
        dev.langchain4j.model.output.TokenUsage langchainUsage = response.tokenUsage();
        
        if (langchainUsage != null) {
            BigDecimal cost = calculateCost(langchainUsage.inputTokenCount());
            saveTokenUsage(langchainUsage, cost);
        }
        
        return response;
    }

    private BigDecimal calculateCost(Integer inputTokens) {
        if (inputTokens == null || inputTokens == 0) {
            return BigDecimal.ZERO;
        }
        // Tính toán chi phí dựa trên giá mỗi 1 triệu token
        return new BigDecimal(inputTokens)
                .multiply(COST_PER_1M_TOKENS)
                .divide(new BigDecimal(1_000_000));
    }

    private void saveTokenUsage(dev.langchain4j.model.output.TokenUsage langchainUsage, BigDecimal cost) {
        User currentUser = UserUtils.getCurrentUser(userRepository);
        if (currentUser == null) {
            // Không ghi log nếu không có người dùng (ví dụ: tác vụ chạy nền)
        	log.warn("[COST_TRACKING] Không thể ghi nhận embedding token usage vì không tìm thấy người dùng (currentUser is null).");
            return;
        }

        TokenUsage usage = new TokenUsage();
        usage.setModelName(this.modelName);
        usage.setInputTokens(langchainUsage.inputTokenCount());
        // Model embedding thường chỉ có input token
        usage.setOutputTokens(langchainUsage.outputTokenCount() != null ? langchainUsage.outputTokenCount() : 0);
        usage.setTotalTokens(langchainUsage.totalTokenCount());
        usage.setCost(cost);
        usage.setUser(currentUser);
        usage.setCreatedAt(LocalDateTime.now());

        tokenUsageRepository.save(usage);
    }
}