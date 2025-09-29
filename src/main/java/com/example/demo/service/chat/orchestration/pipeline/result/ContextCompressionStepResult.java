package com.example.demo.service.chat.orchestration.pipeline.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContextCompressionStepResult {
    // Kết quả của bước này là một chuỗi ngữ cảnh đã được nén
    private final String compressedContextString;
}