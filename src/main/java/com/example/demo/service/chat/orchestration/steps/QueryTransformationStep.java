package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryTransformationStep implements PipelineStep {

    private final ChatLanguageModel chatLanguageModel;
    private final QueryRouterService queryRouterService;
    
    @Override
    public String getStepName() {
        return "query-transformation"; 
    }

    @Override
    @LogExecutionTime
    public RagContext execute(RagContext context) {
        String originalQuery = context.getInitialQuery();
        QueryRouterService.QueryType queryType = queryRouterService.getQueryType(originalQuery);

        if (queryType == QueryRouterService.QueryType.COMPLEX) {
            log.debug("Query is complex. Applying Multi-Query transformation...");
            // Sinh ra nhiều câu hỏi
            List<String> queryVariations = generateQueryVariations(originalQuery);
            
            // Hiện tại, RetrievalStep chỉ dùng một câu truy vấn.
            // Để tìm kiếm trên nhiều biến thể, bạn có hai hướng:
            // 1. Chạy RetrievalStep nhiều lần cho mỗi biến thể và gộp kết quả. (Phức tạp hơn)
            // 2. Gộp các biến thể thành một chuỗi truy vấn duy nhất. (Đơn giản hơn)
            String combinedQuery = String.join(" ", queryVariations);

            context.setTransformedQuery(combinedQuery);
            log.info("Query transformed from '{}' to '{}'", originalQuery, combinedQuery);
        } else {
            log.debug("Query is simple. Using original query for retrieval.");
            context.setTransformedQuery(originalQuery);
        }
        return context;
    }

    private List<String> generateQueryVariations(String query) {
        try {
            String prompt = String.format(
                "Bạn là một trợ lý AI hữu ích. Nhiệm vụ của bạn là tạo ra 3 phiên bản khác nhau của một câu hỏi từ người dùng đã cho, " +
                "để có thể sử dụng chúng để truy xuất các tài liệu liên quan từ một kho vector. " +
                "Hãy tập trung vào việc tạo ra các câu hỏi có thể nắm bắt được các khía cạnh khác nhau hoặc ý định tiềm ẩn của câu hỏi gốc. " +
                "Trả lời bằng một danh sách các câu hỏi, mỗi câu hỏi trên một dòng, không có đánh số hay gạch đầu dòng.\n" +
                "Câu hỏi gốc: '%s'", query
            );

            String response = chatLanguageModel.generate(prompt);
            
            // Trả về câu hỏi gốc cùng với các biến thể
            List<String> variations = new ArrayList<>(Arrays.asList(response.split("\\n")));
            variations.add(query); // Luôn bao gồm cả câu hỏi gốc

            return variations.stream().filter(q -> !q.trim().isEmpty()).distinct().collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Failed to generate query variations for: '{}'. Falling back to original query.", query, e);
            return List.of(query);
        }
    }
}