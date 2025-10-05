package com.example.demo.service.chat.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface RouterAgent {

    @SystemMessage({
        "Bạn là một trợ lý AI điều phối thông minh và hiệu quả.",
        "Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và chọn một và chỉ một công cụ phù hợp nhất từ danh sách có sẵn để trả lời.",
        "Bạn không được tự trả lời câu hỏi, bạn PHẢI sử dụng công cụ được cung cấp.",
        "Hãy dựa vào mô tả của từng công cụ để đưa ra lựa chọn chính xác nhất."
    })
    String chat(@MemoryId Long sessionId, @UserMessage String userMessage);
}