package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.config.pipeline.PipelineConfig;
import com.example.demo.service.chat.orchestration.context.RagContext;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        PipelineConfig.PipelineDefinition definition = pipelineConfig.getDefinitions().get(pipelineName);
        if (definition == null) {
            throw new IllegalArgumentException("Pipeline không tồn tại: " + pipelineName);
        }

        RagContext currentContext = initialContext;
        for (String stepName : definition.getSteps()) {
            PipelineStep step = steps.get(stepName);
            if (step == null) {
                throw new IllegalStateException("Step không tồn tại trong Spring context: " + stepName);
            }
            currentContext = step.execute(currentContext);
        }
        return currentContext;
    }
}