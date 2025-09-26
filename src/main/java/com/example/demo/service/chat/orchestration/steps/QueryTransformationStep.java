// src/main/java/com/example/demo/service/chat/orchestration/steps/QueryTransformationStep.java
package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
// KHÔNG CẦN import QueryRouterService nữa

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
    // ✅ Bỏ QueryRouterService khỏi dependency

    // ✅ Enum này giờ nằm bên trong Step để thể hiện logic cục bộ
    private enum QueryComplexity {
        SIMPLE,
        COMPLEX
    }
    
    @Override
    public String getStepName() {
        return "query-transformation"; 
    }

    @Override
    @LogExecutionTime
    public RagContext execute(RagContext context) {
        String originalQuery = context.getInitialQuery();
        
        // Bước 1: Phân loại độ phức tạp của câu hỏi (logic được chuyển vào đây)
        QueryComplexity complexity = classifyQueryComplexity(originalQuery);

        // Bước 2: Chỉ biến đổi nếu câu hỏi phức tạp
        if (complexity == QueryComplexity.COMPLEX) {
            log.debug("Query is complex. Applying Multi-Query transformation...");
            List<String> queryVariations = generateQueryVariations(originalQuery);
            
            String combinedQuery = String.join(" ", queryVariations);

            context.setTransformedQuery(combinedQuery);
            log.info("Query transformed from '{}' to '{}'", originalQuery, combinedQuery);
        } else {
            log.debug("Query is simple. Using original query for retrieval.");
            context.setTransformedQuery(originalQuery);
        }
        return context;
    }

    /**
     * ✅ Logic phân loại từ QueryRouterService cũ được chuyển vào đây.
     * Giờ nó là một phương thức private của Step này.
     */
    private QueryComplexity classifyQueryComplexity(String query) {
        try {
            String systemPrompt = """
                Bạn là một AI phân loại truy vấn cực kỳ hiệu quả và nhanh chóng.
                Nhiệm vụ của bạn là đọc truy vấn của người dùng và quyết định xem nó có cần được "làm giàu" thông tin (biến đổi) trước khi tìm kiếm hay không.

                Phân loại vào MỘT trong hai loại sau:

                1.  **COMPLEX**:
                    - Câu hỏi mở, yêu cầu phân tích, so sánh, giải thích sâu (ví dụ: "tại sao...", "phân tích ưu nhược điểm...", "so sánh A và B").
                    - Câu hỏi giả định, hỏi về các khái niệm trừu tượng.
                    - Câu hỏi có thể mơ hồ, ngắn gọn nhưng hàm ý một nhu cầu thông tin phức tạp.
                    - Ví dụ: "giải thích về kiến trúc microservices", "so sánh ưu và nhược điểm của React và Vue".

                2.  **SIMPLE**:
                    - Câu hỏi tìm kiếm thông tin cụ thể, từ khóa rõ ràng (fact-based).
                    - Các câu chào hỏi, nói chuyện phiếm đơn giản.
                    - Ví dụ: "thủ đô của Pháp là gì?", "xin chào", "tóm tắt file X".

                Chỉ trả lời bằng MỘT TỪ: SIMPLE hoặc COMPLEX.
                """;

            String response = chatLanguageModel.generate(systemPrompt + "\n\nTruy vấn người dùng: \"" + query + "\"");

            if (response.trim().equalsIgnoreCase("COMPLEX")) {
                log.debug("Internal classification: COMPLEX.");
                return QueryComplexity.COMPLEX;
            }
        } catch (Exception e) {
            log.warn("Query complexity classification failed: {}. Falling back to SIMPLE.", e.getMessage());
        }
        
        log.debug("Internal classification: SIMPLE.");
        return QueryComplexity.SIMPLE;
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
            
            List<String> variations = new ArrayList<>(Arrays.asList(response.split("\\n")));
            variations.add(query); 

            return variations.stream().filter(q -> !q.trim().isEmpty()).distinct().collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Failed to generate query variations for: '{}'. Falling back to original query.", query, e);
            return List.of(query);
        }
    }
}