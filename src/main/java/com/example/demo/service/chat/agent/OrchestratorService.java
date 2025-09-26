// src/main/java/com/example/demo/service/chat/agent/OrchestratorService.java
package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    private final QueryRouterService queryRouter;
    private final ToolAgent toolAgent;
    private final Agent ragAgent; // Giữ lại RAGAgent
    private final Agent chitChatAgent; // Giữ lại ChitChatAgent

    // ✅ Constructor được đơn giản hóa rất nhiều
    public OrchestratorService(QueryRouterService queryRouter,
                               ToolAgent toolAgent,
                               List<Agent> agentList) { // Spring sẽ inject tất cả các bean implement Agent vào đây
        this.queryRouter = queryRouter;
        this.toolAgent = toolAgent;

        // Tìm và gán các agent cần thiết từ danh sách
        Map<String, Agent> agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.ragAgent = agents.get("RAGAgent");
        this.chitChatAgent = agents.get("ChitChatAgent");

        if (this.ragAgent == null || this.chitChatAgent == null) {
            throw new IllegalStateException("RAGAgent or ChitChatAgent not found in the application context!");
        }
    }

    /**
     * Phương thức điều phối chính, nhận câu hỏi của người dùng và định tuyến
     * đến agent phù hợp.
     * @param userMessage Câu hỏi từ người dùng.
     * @param context Ngữ cảnh RagContext để tương thích với các agent hiện tại.
     * @return Câu trả lời từ agent được chọn.
     */
    public String orchestrate(String userMessage, RagContext context) {
        // Bước 1: Lấy kết quả định tuyến từ QueryRouterService
        String route = queryRouter.route(userMessage);
        log.info("Query: '{}' -> Routed to: {}", userMessage, route);

        // Bước 2: Dựa vào kết quả để gọi agent tương ứng
        if (route.toUpperCase().contains("TOOL")) {
            // Đối với ToolAgent, nó trả về String trực tiếp
            return toolAgent.chat(userMessage);

        } else if (route.toUpperCase().contains("RAG")) {
            // Đối với các Agent cũ, chúng ta vẫn truyền RagContext
            ragAgent.execute(context);
            return context.getReply();

        } else { // Mặc định là CHITCHAT
            chitChatAgent.execute(context);
            return context.getReply();
        }
    }
}