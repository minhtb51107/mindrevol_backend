package com.example.demo.service.chat.orchestration.rules;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FollowUpQueryDetectionService {

    // Các từ khóa này cho thấy người dùng có khả năng đang hỏi tiếp về chủ đề trước đó.
    private static final List<String> FOLLOW_UP_KEYWORDS = List.of(
            "thêm thông tin", "phân tích thêm", "cụ thể hơn", "về điều đó", "về nó",
            "còn gì nữa không", "nó là gì", "tại sao vậy", "giải thích đi", "dựa trên"
    );

    /**
     * Kiểm tra xem một câu hỏi có phải là câu hỏi nối tiếp hay không.
     * @param question Câu hỏi của người dùng.
     * @return true nếu là câu hỏi nối tiếp, false nếu không.
     */
    public boolean isFollowUp(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lowerCaseQuestion = question.toLowerCase();
        for (String keyword : FOLLOW_UP_KEYWORDS) {
            if (lowerCaseQuestion.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}