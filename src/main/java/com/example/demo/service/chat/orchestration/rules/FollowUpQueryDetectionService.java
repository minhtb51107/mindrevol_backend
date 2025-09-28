package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

// Sửa 1: Chuyển từ class thành interface
public interface FollowUpQueryDetectionService {

    // Sửa 2: Nhúng logic keyword vào SystemMessage
    @SystemMessage(
        """
        You are a boolean logic expert. Based on the user's latest query, determine if it is a follow-up question.
        A follow-up question asks for more details on a previously discussed topic or contains keywords like:
        "thêm thông tin", "phân tích thêm", "cụ thể hơn", "về điều đó", "về nó", "còn gì nữa không", "nó là gì", "tại sao vậy", "giải thích đi", "dựa trên".
        Respond with only "true" or "false".
        """
    )
    boolean isFollowUp(@UserMessage String query);
}