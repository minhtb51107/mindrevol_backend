package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.config.pipeline.PipelineConfig;
import com.example.demo.service.chat.fallback.FallbackService; // ✅ IMPORT THÊM
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.result.GenerationStepResult;
import com.example.demo.service.chat.orchestration.pipeline.result.RetrievalStepResult;
import dev.langchain4j.data.segment.TextSegment; // ✅ IMPORT THÊM
import dev.langchain4j.store.embedding.EmbeddingMatch; // ✅ IMPORT THÊM
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PipelineManager {

    private final PipelineConfig pipelineConfig;
    private final Map<String, PipelineStep<?>> steps;
    private final FallbackService fallbackService; // ✅ THÊM DEPENDENCY

    public PipelineManager(PipelineConfig pipelineConfig, List<PipelineStep<?>> stepList, FallbackService fallbackService) {
        this.pipelineConfig = pipelineConfig;
        this.steps = stepList.stream()
                .collect(Collectors.toMap(PipelineStep::getStepName, Function.identity()));
        this.fallbackService = fallbackService; // ✅ KHỞI TẠO
    }

    public RagContext run(RagContext initialContext, String pipelineName) {
        log.info("Bắt đầu thực thi pipeline: '{}'", pipelineName);

        PipelineConfig.PipelineDefinition definition = pipelineConfig.getPipelines().get(pipelineName);

        if (definition == null) {
            log.error("Không tìm thấy định nghĩa cho pipeline: '{}'", pipelineName);
            throw new IllegalArgumentException("Pipeline không tồn tại: " + pipelineName);
        }

        RagContext currentContext = initialContext;
        log.debug("Các bước thực thi của pipeline '{}': {}", pipelineName, definition.getSteps());

        for (String stepName : definition.getSteps()) {
            PipelineStep<?> step = steps.get(stepName);
            if (step == null) {
                log.error("Step '{}' được định nghĩa trong pipeline '{}' nhưng không tồn tại trong Spring context.", stepName, pipelineName);
                throw new IllegalStateException("Step không tồn tại trong Spring context: " + stepName);
            }
            log.info(">> Đang thực thi step: '{}'", stepName);

            // Thực thi step và nhận kết quả
            Object result = step.execute(currentContext);

            // Cập nhật context dựa trên kết quả
            currentContext = updateContext(currentContext, result);

            // --- ✅ LOGIC FALLBACK ---
            // Kiểm tra ngay sau khi bước retrieval hoàn tất
            if ("retrieval".equals(stepName)) {
                List<EmbeddingMatch<TextSegment>> matches = currentContext.getRetrievedMatches();
                if (matches == null || matches.isEmpty()) {
                    log.warn("Không tìm thấy kết quả nào từ bước retrieval cho session {}. Kích hoạt fallback.", currentContext.getSession().getId());
                    // Lấy câu trả lời fallback
                    String fallbackResponse = fallbackService.getNoRetrievalResultResponse();
                    // Đặt câu trả lời vào context
                    currentContext.setReply(fallbackResponse);
                    // Dừng pipeline tại đây
                    log.info("Pipeline '{}' đã dừng và trả về thông báo fallback.", pipelineName);
                    return currentContext;
                }
            }
        }

        log.info("Hoàn thành thực thi pipeline: '{}'", pipelineName);
        return currentContext;
    }

    private RagContext updateContext(RagContext context, Object result) {
        if (result instanceof RetrievalStepResult res) {
            context.setQueryEmbedding(res.getQueryEmbedding());
            context.setMetadataFilter(res.getMetadataFilter());
            context.setRetrievedMatches(res.getRetrievedMatches());
        } else if (result instanceof GenerationStepResult res) {
            context.setFinalLcMessages(res.getFinalLcMessages());
            context.setReply(res.getReply());
        }
        // Thêm các case khác cho các step result khác...
        return context;
    }
}