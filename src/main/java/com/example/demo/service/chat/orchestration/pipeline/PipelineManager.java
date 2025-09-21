package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.config.pipeline.PipelineConfig;
import com.example.demo.service.chat.orchestration.context.RagContext;
import lombok.extern.slf4j.Slf4j; // <-- Thêm import cho logging
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j // <-- Thêm annotation Slf4j
@Service
public class PipelineManager {

    private final PipelineConfig pipelineConfig;
    private final Map<String, PipelineStep> steps;

    public PipelineManager(PipelineConfig pipelineConfig, List<PipelineStep> stepList) {
        this.pipelineConfig = pipelineConfig;
        this.steps = stepList.stream()
                .collect(Collectors.toMap(PipelineStep::getStepName, Function.identity()));
    }

    public RagContext run(RagContext initialContext, String pipelineName) {
        log.info("Bắt đầu thực thi pipeline: '{}'", pipelineName);

        // === THAY ĐỔI DUY NHẤT LÀ Ở ĐÂY ===
        // Lấy định nghĩa pipeline trực tiếp từ `pipelines` thay vì `definitions`
        PipelineConfig.PipelineDefinition definition = pipelineConfig.getPipelines().get(pipelineName);
        // ===================================

        if (definition == null) {
            log.error("Không tìm thấy định nghĩa cho pipeline: '{}'", pipelineName);
            throw new IllegalArgumentException("Pipeline không tồn tại: " + pipelineName);
        }

        RagContext currentContext = initialContext;
        log.debug("Các bước thực thi của pipeline '{}': {}", pipelineName, definition.getSteps());

        for (String stepName : definition.getSteps()) {
            PipelineStep step = steps.get(stepName);
            if (step == null) {
                log.error("Step '{}' được định nghĩa trong pipeline '{}' nhưng không tồn tại trong Spring context.", stepName, pipelineName);
                throw new IllegalStateException("Step không tồn tại trong Spring context: " + stepName);
            }
            log.info(">> Đang thực thi step: '{}'", stepName);
            currentContext = step.execute(currentContext);
        }

        log.info("Hoàn thành thực thi pipeline: '{}'", pipelineName);
        return currentContext;
    }
}