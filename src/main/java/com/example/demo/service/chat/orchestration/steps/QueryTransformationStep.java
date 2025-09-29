package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel; // ✅ 1. IMPORT
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryTransformationStep implements PipelineStep {

    private final ChatLanguageModel chatLanguageModel;

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
        
        // Bước 1: Phân loại độ phức tạp (ĐÃ TÍCH HỢP CALL IDENTIFIER)
        QueryComplexity complexity = classifyQueryComplexity(originalQuery);

        // Bước 2: Chỉ biến đổi nếu câu hỏi phức tạp (ĐÃ TÍCH HỢP CALL IDENTIFIER)
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

    private QueryComplexity classifyQueryComplexity(String query) {
        try {
            String systemPrompt = """
                Bạn là một AI phân loại truy vấn cực kỳ hiệu quả và nhanh chóng.
                Nhiệm vụ của bạn là đọc truy vấn của người dùng và quyết định xem nó có cần được "làm giàu" thông tin (biến đổi) trước khi tìm kiếm hay không.
                Phân loại vào MỘT trong hai loại sau:
                1.  **COMPLEX**: Câu hỏi mở, yêu cầu phân tích, so sánh, giải thích sâu (ví dụ: "tại sao...", "phân tích ưu nhược điểm...", "so sánh A và B").
                2.  **SIMPLE**: Câu hỏi tìm kiếm thông tin cụ thể, từ khóa rõ ràng (fact-based).
                Chỉ trả lời bằng MỘT TỪ: SIMPLE hoặc COMPLEX.
                """;

            // ✅ 2. XÂY DỰNG MESSAGE VÀ GỌI BẰNG PHƯƠNG THỨC MỚI
            List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("Truy vấn người dùng: \"" + query + "\"")
            );

            Response<AiMessage> response;
            if (chatLanguageModel instanceof TrackedChatLanguageModel) {
                response = ((TrackedChatLanguageModel) chatLanguageModel).generate(messages, "query_classification");
            } else {
                log.warn("Cost tracking for 'query_classification' is skipped as model is not a TrackedChatLanguageModel.");
                response = chatLanguageModel.generate(messages);
            }

            if (response.content().text().trim().equalsIgnoreCase("COMPLEX")) {
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
            String systemPrompt = "Bạn là một trợ lý AI hữu ích. Nhiệm vụ của bạn là tạo ra 3 phiên bản khác nhau của một câu hỏi từ người dùng đã cho, " +
                "để có thể sử dụng chúng để truy xuất các tài liệu liên quan từ một kho vector. " +
                "Hãy tập trung vào việc tạo ra các câu hỏi có thể nắm bắt được các khía cạnh khác nhau hoặc ý định tiềm ẩn của câu hỏi gốc. " +
                "Trả lời bằng một danh sách các câu hỏi, mỗi câu hỏi trên một dòng, không có đánh số hay gạch đầu dòng.";

            // ✅ 3. XÂY DỰNG MESSAGE VÀ GỌI BẰNG PHƯƠNG THỨC MỚI
            List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("Câu hỏi gốc: '" + query + "'")
            );
            
            Response<AiMessage> response;
            if (chatLanguageModel instanceof TrackedChatLanguageModel) {
                response = ((TrackedChatLanguageModel) chatLanguageModel).generate(messages, "query_variation_generation");
            } else {
                log.warn("Cost tracking for 'query_variation_generation' is skipped as model is not a TrackedChatLanguageModel.");
                response = chatLanguageModel.generate(messages);
            }
            
            List<String> variations = new ArrayList<>(Arrays.asList(response.content().text().split("\\n")));
            variations.add(query); 

            return variations.stream().filter(q -> !q.trim().isEmpty()).distinct().collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Failed to generate query variations for: '{}'. Falling back to original query.", query, e);
            return Collections.singletonList(query);
        }
    }
}