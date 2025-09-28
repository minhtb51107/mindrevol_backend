package com.example.demo.service.chat.orchestration.rules;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QueryIntentClassificationService {

    private static final List<String> DYNAMIC_KEYWORDS = List.of(
            "giá cổ phiếu", "thị trường chứng khoán", "tin tức", "mới nhất",
            "thời tiết", "bây giờ là mấy giờ", "hôm nay", "hiện tại"
    );

    // Có thể thêm các từ khóa cho RAG hoặc Chit-chat nếu cần
    // private static final List<String> RAG_KEYWORDS = List.of(...)

    /**
     * Phân loại ý định của câu hỏi dựa trên nội dung.
     * @param question Câu hỏi của người dùng.
     * @return QueryIntent được phân loại.
     */
    public QueryIntent classify(String question) {
        String lowerCaseQuestion = question.toLowerCase();

        for (String keyword : DYNAMIC_KEYWORDS) {
            if (lowerCaseQuestion.contains(keyword)) {
                return QueryIntent.DYNAMIC_QUERY;
            }
        }

        // Hiện tại, chúng ta mặc định các câu hỏi khác là STATIC_QUERY để có thể cache.
        // Logic này có thể được mở rộng để nhận diện RAG_QUERY hoặc CHIT_CHAT.
        return QueryIntent.STATIC_QUERY;
    }
}