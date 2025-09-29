package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.config.pipeline.PipelineConfig;
import com.example.demo.service.chat.QuestionAnswerCacheService; // ✅ 1. THÊM IMPORT
import com.example.demo.service.chat.fallback.FallbackService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.result.ContextCompressionStepResult;
import com.example.demo.service.chat.orchestration.pipeline.result.GenerationStepResult;
import com.example.demo.service.chat.orchestration.pipeline.result.RetrievalStepResult;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional; // ✅ 1. THÊM IMPORT
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PipelineManager {

    private final PipelineConfig pipelineConfig;
    private final Map<String, PipelineStep<?>> steps;
    private final FallbackService fallbackService;
    private final QuestionAnswerCacheService cacheService; // ✅ 2. THÊM DEPENDENCY

    public PipelineManager(
            PipelineConfig pipelineConfig,
            List<PipelineStep<?>> stepList,
            FallbackService fallbackService,
            QuestionAnswerCacheService cacheService // ✅ 2. INJECT VÀO CONSTRUCTOR
    ) {
        this.pipelineConfig = pipelineConfig;
        this.steps = stepList.stream()
                .collect(Collectors.toMap(PipelineStep::getStepName, Function.identity()));
        this.fallbackService = fallbackService;
        this.cacheService = cacheService; // ✅ 2. KHỞI TẠO
    }

    public RagContext run(RagContext initialContext, String pipelineName) {
        log.info("Bắt đầu thực thi pipeline: '{}' cho session {}", pipelineName, initialContext.getSession().getId());

        // ✅ 3. LOGIC KIỂM TRA CACHE NGAY TỪ ĐẦU
        String userQuery = initialContext.getInitialQuery();
        // Giả sử lastBotMessage được lấy từ context hoặc service khác nếu cần
        String lastBotMessage = null; // Hoặc `initialContext.getLastBotMessage()` nếu có
        Optional<String> cachedAnswer = cacheService.findCachedAnswer(userQuery, lastBotMessage);

        if (cachedAnswer.isPresent()) {
            log.info("CACHE HIT! Trả về câu trả lời từ cache cho session {}.", initialContext.getSession().getId());
            initialContext.setReply(cachedAnswer.get());
            // Dừng pipeline và trả về ngay lập tức
            return initialContext; 
        }
        
        log.info("CACHE MISS. Bắt đầu chạy đầy đủ pipeline cho session {}.", initialContext.getSession().getId());
        // --- KẾT THÚC LOGIC KIỂM TRA CACHE ---

        PipelineConfig.PipelineDefinition definition = pipelineConfig.getPipelines().get(pipelineName);
        if (definition == null) {
            log.error("Không tìm thấy định nghĩa cho pipeline: '{}'", pipelineName);
            throw new IllegalArgumentException("Pipeline không tồn tại: " + pipelineName);
        }

        RagContext currentContext = initialContext;
        log.debug("Các bước thực thi của pipeline '{}': {}", pipelineName, definition.getSteps());

        for (String stepName : definition.getSteps()) {
            // ... (Phần logic chạy các step và fallback giữ nguyên) ...
            PipelineStep<?> step = steps.get(stepName);
            if (step == null) {
                log.error("Step '{}' được định nghĩa trong pipeline '{}' nhưng không tồn tại trong Spring context.", stepName, pipelineName);
                throw new IllegalStateException("Step không tồn tại trong Spring context: " + stepName);
            }
            log.info(">> Đang thực thi step: '{}'", stepName);

            Object result = step.execute(currentContext);
            currentContext = updateContext(currentContext, result);

            if ("retrieval".equals(stepName)) {
                List<EmbeddingMatch<TextSegment>> matches = currentContext.getRetrievedMatches();
                if (matches == null || matches.isEmpty()) {
                    log.warn("Không tìm thấy kết quả nào từ bước retrieval cho session {}. Kích hoạt fallback.", currentContext.getSession().getId());
                    String fallbackResponse = fallbackService.getNoRetrievalResultResponse();
                    currentContext.setReply(fallbackResponse);
                    log.info("Pipeline '{}' đã dừng và trả về thông báo fallback.", pipelineName);
                    return currentContext;
                }
            }
        }

        log.info("Hoàn thành thực thi pipeline: '{}'", pipelineName);
        
        // ✅ 4. LƯU KẾT QUẢ VÀO CACHE SAU KHI PIPELINE CHẠY XONG
        if (currentContext.getReply() != null && !currentContext.getReply().isBlank()) {
            log.info("Lưu câu trả lời mới vào cache cho session {}.", currentContext.getSession().getId());
            // Thời gian cache hợp lệ, ví dụ 24 giờ
            ZonedDateTime validUntil = ZonedDateTime.now().plusHours(24);
            cacheService.saveToCache(userQuery, currentContext.getReply(), lastBotMessage, Map.of(), validUntil);
        }
        
        return currentContext;
    }

    // ... (Phương thức updateContext giữ nguyên) ...
    private RagContext updateContext(RagContext context, Object result) {
        if (result instanceof RetrievalStepResult res) {
            context.setQueryEmbedding(res.getQueryEmbedding());
            context.setMetadataFilter(res.getMetadataFilter());
            context.setRetrievedMatches(res.getRetrievedMatches());
        } else if (result instanceof GenerationStepResult res) {
            context.setFinalLcMessages(res.getFinalLcMessages());
            context.setReply(res.getReply());
        } else if (result instanceof ContextCompressionStepResult res) {
            context.setRagContextString(res.getCompressedContextString());
        }
        return context;
    }
}