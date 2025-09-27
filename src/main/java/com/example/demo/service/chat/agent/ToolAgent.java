// src/main/java/com/example/demo/service/chat/agent/ToolAgent.java
package com.example.demo.service.chat.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ToolAgent {

    // ✅ PROMPT MỚI: Cực kỳ nghiêm khắc và không cho phép hỏi lại.
    @SystemMessage({
            "Bạn là một AI thực thi công cụ.",
            "Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và các công cụ có sẵn.",
            "NẾU câu hỏi có thể được trả lời bằng một công cụ, bạn PHẢI sử dụng công cụ đó.",
            "NẾU không có công cụ nào phù hợp, bạn PHẢI trả lời rằng bạn không thể giúp.",
            "KHÔNG ĐƯỢC PHÉP đặt câu hỏi ngược lại cho người dùng."
    })
    String chat(@MemoryId Long sessionId, @UserMessage String userMessage);
}