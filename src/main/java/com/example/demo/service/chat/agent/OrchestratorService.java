// src/main/java/com/example/demo/service/chat/agent/OrchestratorService.java
package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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
    private final Agent ragAgent;
    private final Agent chitChatAgent;
    private final ChatMessageService chatMessageService;
    private final Agent memoryQueryAgent;
    private final Agent toolAgent; // Bây giờ đây là ToolExecutorAgent

    public OrchestratorService(QueryRouterService queryRouter,
                               List<Agent> agentList, // Sửa để nhận List<Agent>
                               ChatMessageService chatMessageService) {
        this.queryRouter = queryRouter;
        this.chatMessageService = chatMessageService;

        Map<String, Agent> agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.ragAgent = agents.get("RAGAgent");
        this.chitChatAgent = agents.get("ChitChatAgent");
        this.memoryQueryAgent = agents.get("MemoryQueryAgent");
        this.toolAgent = agents.get("ToolAgent"); // Lấy ToolExecutorAgent từ context

        if (this.ragAgent == null || this.chitChatAgent == null || this.memoryQueryAgent == null || this.toolAgent == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
    }

    public String orchestrate(String userMessage, RagContext context) {
        String route = queryRouter.route(userMessage);
        log.info("Query: '{}' -> Routed to: {}", userMessage, route.toUpperCase());
        String upperCaseRoute = route.toUpperCase();
        
        String response; // Khai báo biến response ở ngoài

        // ✅ THAY ĐỔI QUAN TRỌNG: Thống nhất cách gọi agent
        if (upperCaseRoute.contains("TOOL")) {
            toolAgent.execute(context); // Gọi qua interface Agent chung
            response = context.getReply();
        } else if (upperCaseRoute.contains("MEMORY_QUERY")) {
            memoryQueryAgent.execute(context);
            response = context.getReply();
        } else if (upperCaseRoute.contains("RAG")) {
            ragAgent.execute(context);
            response = context.getReply();
        } else { // Mặc định là CHITCHAT
            chitChatAgent.execute(context);
            response = context.getReply();
        }
        
        // ✅ TẤT CẢ CÁC LUỒNG ĐỀU ĐI QUA ĐÂY ĐỂ LƯU TRỮ MỘT CÁCH NHẤT QUÁN
        if (response != null && !response.isEmpty()) {
            saveConversationAndUpdateMemory(context, userMessage, response);
        }

        return response;
    }

    private void saveConversationAndUpdateMemory(RagContext context, String userMessage, String assistantResponse) {
        try {
            // 1. Lưu vào bộ nhớ dài hạn (PostgreSQL)
            chatMessageService.saveMessage(context.getSession(), "user", userMessage);
            chatMessageService.saveMessage(context.getSession(), "assistant", assistantResponse);
            log.info("Orchestrator saved conversation to DB for session {}", context.getSession().getId());

            // 2. Cập nhật bộ nhớ ngắn hạn (Redis, thông qua đối tượng ChatMemory)
            context.getChatMemory().add(UserMessage.from(userMessage));
            context.getChatMemory().add(AiMessage.from(assistantResponse));
            log.info("Orchestrator updated short-term memory for session {}", context.getSession().getId());
        } catch (Exception e) {
            log.error("Error during centralized persistence in Orchestrator for session {}: {}", context.getSession().getId(), e.getMessage(), e);
        }
    }
}