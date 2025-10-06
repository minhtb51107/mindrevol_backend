package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Định nghĩa một interface chung cho các agent có khả năng trò chuyện.
 * Phương thức chat sẽ nhận vào toàn bộ RagContext để có thể truy cập
 * sessionId và rewrittenQuery một cách linh hoạt.
 */
public interface ToolUsingAgent {

    /**
     * Phương thức trò chuyện chính.
     * LangChain4j sẽ tự động trích xuất sessionId từ context.getSession().getId()
     * và rewrittenQuery từ context.getQuery() để sử dụng.
     *
     * @param context Đối tượng chứa toàn bộ ngữ cảnh của cuộc trò chuyện.
     * @return Câu trả lời từ AI.
     */
    String chat(RagContext context);
}