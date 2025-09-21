package com.example.demo.config.pipeline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties
@Data
public class PipelineConfig {

    private Map<String, PipelineDefinition> pipelines;

    @Data
    public static class PipelineDefinition {
        private List<String> steps;
    }
}