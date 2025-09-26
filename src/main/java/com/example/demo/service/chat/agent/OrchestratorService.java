// src/main/java/com/example/demo/service/chat/agent/OrchestratorService.java
package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel; // ✅ 1. Import service mới
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    // ✅ 2. Thay thế QueryRouterService bằng TrackedChatLanguageModel
    private final TrackedChatLanguageModel trackedChatLanguageModel;
    private final ChatMessageService chatMessageService;
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;

    // ✅ 3. Cập nhật Constructor
    public OrchestratorService(List<Agent> agentList,
                               TrackedChatLanguageModel trackedChatLanguageModel,
                               ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
        this.trackedChatLanguageModel = trackedChatLanguageModel;

        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        // Bạn có thể chọn một agent mặc định khác nếu muốn, ví dụ ChitChatAgent
        this.defaultAgent = this.agents.get("RAGAgent"); 

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(String userMessage, RagContext context) {
        // ✅ 4. Gọi phương thức chooseAgent mới để định tuyến và theo dõi token
        Agent chosenAgent = chooseAgent(userMessage, context);
        
        // Thực thi agent đã được chọn
        chosenAgent.execute(context);
        String response = context.getReply();
        
        // Luồng lưu trữ hội thoại được giữ nguyên
        if (response != null && !response.isEmpty()) {
            saveConversationAndUpdateMemory(context, userMessage, response);
        }

        return response;
    }

    // ✅ 5. Phương thức mới để chọn Agent và theo dõi token
    private Agent chooseAgent(String userMessage, RagContext context) {
        String prompt = buildOrchestratorPrompt(userMessage);

        log.info("Choosing agent for query: '{}'", userMessage);

        // Gọi LLM thông qua service đã tích hợp tracking
        Response<AiMessage> response = trackedChatLanguageModel.generate(
                Collections.singletonList(new UserMessage(prompt)),
                context.getUser().getId(),
                context.getSession().getId()
        );
        
        String chosenAgentName = response.content().text().trim();
        log.info("Query: '{}' -> Routed to: {}", userMessage, chosenAgentName.toUpperCase());

        Agent chosenAgent = agents.get(chosenAgentName);

        if (chosenAgent == null) {
            log.warn("Could not find agent named '{}'. Falling back to default agent '{}'.",
                    chosenAgentName, defaultAgent.getName());
            chosenAgent = defaultAgent;
        }

        return chosenAgent;
    }
    
    // ✅ 6. Phương thức mới để xây dựng prompt cho việc định tuyến
    private String buildOrchestratorPrompt(String userInput) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là một hệ thống định tuyến AI chuyên nghiệp. Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và chọn một agent chuyên biệt phù hợp nhất để xử lý nó.\n");
        promptBuilder.append("Chỉ trả lời bằng tên của agent được chọn. KHÔNG thêm bất kỳ lời giải thích hay dấu câu nào.\n\n");
        promptBuilder.append("Các agent có sẵn:\n");

        // Tự động thêm các agent và mô tả của chúng vào prompt
        for (Agent agent : agents.values()) {
            promptBuilder.append(String.format("- Tên: %s, Mô tả: %s\n", agent.getName(), agent.getDescription()));
        }

        promptBuilder.append("\nCâu hỏi của người dùng: \"").append(userInput).append("\"\n");
        promptBuilder.append("Tên agent được chọn: ");

        return promptBuilder.toString();
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