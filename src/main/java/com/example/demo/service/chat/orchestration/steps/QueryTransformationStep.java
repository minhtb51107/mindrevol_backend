package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryTransformationStep implements RagStep {

    private final ChatLanguageModel chatLanguageModel;
    private final QueryRouterService queryRouterService;

    @Override
    public RagContext execute(RagContext context) {
        String originalQuery = context.getInitialQuery();

        // ✅ SỬ DỤNG ROUTER THÔNG MINH
        QueryRouterService.QueryType queryType = queryRouterService.getQueryType(originalQuery);

        if (queryType == QueryRouterService.QueryType.COMPLEX) {
            log.debug("Query is complex. Applying HyDE transformation...");
            String transformedQuery = generateHypotheticalAnswer(originalQuery);
            context.setTransformedQuery(transformedQuery);
            log.info("Query transformed: '{}' -> '{}'", originalQuery, transformedQuery);
        } else {
            log.debug("Query is simple. Using original query for retrieval.");
            context.setTransformedQuery(originalQuery); // Sử dụng truy vấn gốc
        }
        return context;
    }

    /**
     * Tạo ra một câu trả lời giả định (hypothetical answer) cho câu hỏi.
     * Embedding của câu trả lời này thường giàu ngữ cảnh hơn câu hỏi gốc.
     */
    private String generateHypotheticalAnswer(String query) {
        try {
            String prompt = "Hãy viết một đoạn văn chi tiết để trả lời cho câu hỏi sau đây. " +
                            "Đoạn văn này sẽ được dùng để tìm kiếm thông tin liên quan, " +
                            "vì vậy hãy tập trung vào các từ khóa, khái niệm và ngữ cảnh có thể có trong tài liệu gốc. " +
                            "Câu hỏi: '" + query + "'";
            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Failed to generate hypothetical answer for query: '{}'. Falling back to original query.", query, e);
            return query; // Trả về query gốc nếu có lỗi
        }
    }

    /**
     * Một logic đơn giản để xác định xem truy vấn có cần biến đổi hay không.
     * Có thể thay thế bằng một LLM-based router để có kết quả chính xác hơn.
     */
//    private boolean isComplexQuery(String query) {
//        // Các câu hỏi dài, chứa từ khóa so sánh, phân tích, hoặc yêu cầu giải thích
//        // thường được hưởng lợi từ việc biến đổi.
//        return query.length() > 80 ||
//               query.toLowerCase().matches(".*(so sánh|phân tích|giải thích|tại sao|như thế nào|làm thế nào|khác biệt).*");
//    }
}