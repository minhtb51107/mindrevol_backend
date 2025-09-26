// src/main/java/com/example/demo/service/chat/agent/ToolExecutorAgent.java
package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("ToolAgent")
@RequiredArgsConstructor
public class ToolExecutorAgent implements Agent {

    private final ToolAgent langchainToolAgent;

    @Override
    public String getName() {
        return "ToolAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng khi cần dùng các công cụ bên ngoài như tra cứu thời tiết, giá cổ phiếu, hoặc tìm kiếm trên web.";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing ToolExecutorAgent for query: '{}'", context.getInitialQuery());

        // ✅ THAY ĐỔI QUAN TRỌNG:
        // Lấy session ID từ context để sử dụng làm MemoryId.
        Long sessionId = context.getSession().getId();
        String userQuery = context.getInitialQuery();

        // Gọi phương thức chat đã được cập nhật của ToolAgent
        String response = langchainToolAgent.chat(sessionId, userQuery);

        // Cập nhật câu trả lời vào context
        context.setReply(response);

        // Trả về context đã được cập nhật
        return context;
    }
}