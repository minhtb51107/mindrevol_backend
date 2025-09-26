// src/main/java/com/example/demo/service/chat/agent/OrchestratorService.java
package com.example.demo.service.chat.agent;

import com.example.demo.model.chat.ChatSession;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    private final QueryRouterService queryRouter;
    private final ToolAgent toolAgent;
    private final Agent ragAgent;
    private final Agent chitChatAgent;
    private final ChatMessageService chatMessageService;
    private final Agent memoryQueryAgent;

    public OrchestratorService(QueryRouterService queryRouter,
                               ToolAgent toolAgent,
                               List<Agent> agentList,
                               ChatMessageService chatMessageService) {
        this.queryRouter = queryRouter;
        this.toolAgent = toolAgent;
        this.chatMessageService = chatMessageService;

        Map<String, Agent> agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.ragAgent = agents.get("RAGAgent");
        this.chitChatAgent = agents.get("ChitChatAgent");
        this.memoryQueryAgent = agents.get("MemoryQueryAgent");

        if (this.ragAgent == null || this.chitChatAgent == null || this.memoryQueryAgent == null) {
            throw new IllegalStateException("RAGAgent, ChitChatAgent, or MemoryQueryAgent not found!");
        }
    }

    public String orchestrate(String userMessage, RagContext context) {
        String route = queryRouter.route(userMessage);
        log.info("Query: '{}' -> Routed to: {}", userMessage, route.toUpperCase());
        String upperCaseRoute = route.toUpperCase();

        if (upperCaseRoute.contains("TOOL")) {
            String response = toolAgent.chat(userMessage);
            saveConversation(context.getSession(), userMessage, response); // Tái sử dụng logic lưu
            return response;

        } else if (upperCaseRoute.contains("MEMORY_QUERY")) {
            // Thực thi agent để lấy câu trả lời
            memoryQueryAgent.execute(context);
            String response = context.getReply();

            // ✅ QUAN TRỌNG: Lưu lại cuộc trò chuyện sau khi có câu trả lời
            saveConversation(context.getSession(), userMessage, response);
            return response;

        } else if (upperCaseRoute.contains("RAG")) {
            ragAgent.execute(context);
            // Không cần lưu ở đây, pipeline đã xử lý
            return context.getReply();

        } else { // Mặc định là CHITCHAT
            chitChatAgent.execute(context);
            // Không cần lưu ở đây, pipeline đã xử lý
            return context.getReply();
        }
    }

    /**
     * Phương thức trợ giúp để đóng gói logic lưu tin nhắn, tránh lặp code.
     */
    private void saveConversation(ChatSession session, String userMessage, String assistantResponse) {
        // 1. Lưu tin nhắn của người dùng
        chatMessageService.saveMessage(session, "user", userMessage);
        // 2. Lưu tin nhắn trả lời của AI
        chatMessageService.saveMessage(session, "assistant", assistantResponse);
        log.info("Successfully persisted direct-agent conversation for session {}", session.getId());
    }
}