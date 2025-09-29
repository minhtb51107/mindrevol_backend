package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.context.ContextCompressionService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
// ✅ 1. IMPORT RESULT MỚI
import com.example.demo.service.chat.orchestration.pipeline.result.ContextCompressionStepResult;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
// ✅ 2. KHAI BÁO KIỂU TRẢ VỀ LÀ ContextCompressionStepResult
public class ContextCompressionStep implements PipelineStep<ContextCompressionStepResult> {

    private final ContextCompressionService contextCompressionService;

    @Override
    public String getStepName() {
        // Tên này phải khớp với tên trong application.yml
        return "context-compression";
    }

    @Override
    @LogExecutionTime
    public ContextCompressionStepResult execute(RagContext context) {
        List<TextSegment> documents = context.getRetrievedMatches().stream()
                .map(match -> match.embedded())
                .collect(Collectors.toList());


        if (documents == null || documents.isEmpty()) {
            log.debug("No documents to compress.");
            return ContextCompressionStepResult.builder()
                    .compressedContextString("") // Trả về chuỗi rỗng nếu không có gì để nén
                    .build();
        }

        String query = context.getTransformedQuery() != null ? context.getTransformedQuery() : context.getInitialQuery();

        String compressedContext = contextCompressionService.compressDocumentContext(documents, query);

        log.info("Context compression completed.");

        // ✅ 3. TRẢ VỀ RESULT OBJECT
        return ContextCompressionStepResult.builder()
                .compressedContextString(compressedContext)
                .build();
    }
}