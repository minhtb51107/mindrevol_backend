package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryRouterService {

    private final ChatLanguageModel chatLanguageModel;

    public enum QueryType {
        SIMPLE, // Truy vấn đơn giản, không cần biến đổi
        COMPLEX // Truy vấn phức tạp, cần biến đổi (ví dụ: HyDE)
    }

    /**
     * Sử dụng LLM để phân loại truy vấn là Đơn giản hay Phức tạp.
     * @param query Truy vấn của người dùng.
     * @return Enum QueryType.
     */
    public QueryType getQueryType(String query) {
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
                log.debug("Query classified as COMPLEX.");
                return QueryType.COMPLEX;
            } else {
                log.debug("Query classified as SIMPLE.");
                return QueryType.SIMPLE;
            }
        } catch (Exception e) {
            log.warn("Query routing failed: {}. Falling back to SIMPLE to save resources.", e.getMessage());
            // Mặc định là SIMPLE để tránh tốn thêm tài nguyên nếu router lỗi
            return QueryType.SIMPLE;
        }
    }
}