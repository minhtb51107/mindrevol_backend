package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryTransformationStep implements PipelineStep {

    private final ChatLanguageModel chatLanguageModel;
    private final QueryRouterService queryRouterService;
    
    @Override
    public String getStepName() {
        // ✅ THAY ĐỔI 1: Đổi thành tên duy nhất
        return "query-transformation"; 
    }

    // ✅ THAY ĐỔI 2: Đổi tên phương thức thành "process"
    @Override
    @LogExecutionTime
    public RagContext execute(RagContext context) {
        String originalQuery = context.getInitialQuery();

        QueryRouterService.QueryType queryType = queryRouterService.getQueryType(originalQuery);

        if (queryType == QueryRouterService.QueryType.COMPLEX) {
            log.debug("Query is complex. Applying HyDE transformation...");
            String transformedQuery = generateHypotheticalAnswer(originalQuery);
            context.setTransformedQuery(transformedQuery);
            log.info("Query transformed: '{}' -> '{}'", originalQuery, transformedQuery);
        } else {
            log.debug("Query is simple. Using original query for retrieval.");
            context.setTransformedQuery(originalQuery);
        }
        return context;
    }

    private String generateHypotheticalAnswer(String query) {
        try {
            String prompt = "Hãy viết một đoạn văn chi tiết để trả lời cho câu hỏi sau đây. " +
                            "Đoạn văn này sẽ được dùng để tìm kiếm thông tin liên quan, " +
                            "vì vậy hãy tập trung vào các từ khóa, khái niệm và ngữ cảnh có thể có trong tài liệu gốc. " +
                            "Câu hỏi: '" + query + "'";
            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Failed to generate hypothetical answer for query: '{}'. Falling back to original query.", query, e);
            return query;
        }
    }
}