// src/main/java/com/example/demo/service/chat/agent/OrchestratorService.java
package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
// ✅ 1. Thay thế import từ Tracked... thành RoutingTracked...
import com.example.demo.service.chat.integration.RoutingTrackedChatLanguageModel; 
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired; // Thêm Autowired nếu thiếu
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    // ✅ 2. Thay thế TrackedChatLanguageModel bằng RoutingTrackedChatLanguageModel
    private final RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel;
    private final ChatMessageService chatMessageService;
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;

    // ✅ TẠO MỘT DANH SÁCH CÁC CÂU CHÀO HỎI ĐƠN GIẢN
    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "xin chào", "chào bạn", "chào", "helo", "alo"
    ));

    @Autowired
    public OrchestratorService(List<Agent> agentList,
                               RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel,
                               ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
        this.routingTrackedChatLanguageModel = routingTrackedChatLanguageModel;

        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.defaultAgent = this.agents.get("RAGAgent"); 

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(String userMessage, RagContext context) {
        // ✅ BƯỚC 1: KIỂM TRA QUY TẮC ĐƠN GIẢN TRƯỚC KHI GỌI LLM
        // Bỏ qua việc gọi LLM đắt đỏ nếu đây là một câu chào hỏi đơn giản.
        if (isSimpleGreeting(userMessage)) {
            log.info("Simple greeting detected. Routing directly to ChitChatAgent.");
            Agent chitChatAgent = agents.get("ChitChatAgent");
            if (chitChatAgent != null) {
                // Thực thi agent và lấy câu trả lời
                chitChatAgent.execute(context);
                String response = context.getReply();

                // Vẫn lưu cuộc hội thoại như bình thường
                if (response != null && !response.isEmpty()) {
                    saveConversationAndUpdateMemory(context, userMessage, response);
                }
                return response;
            }
        }

        // Nếu không phải câu chào hỏi đơn giản, tiếp tục luồng xử lý cũ
        Agent chosenAgent = chooseAgent(userMessage, context);
        
        chosenAgent.execute(context);
        String response = context.getReply();
        
        if (response != null && !response.isEmpty()) {
            saveConversationAndUpdateMemory(context, userMessage, response);
        }

        return response;
    }

    /**
     * ✅ BƯỚC 2: TẠO PHƯƠNG THỨC KIỂM TRA (HELPER METHOD)
     * Kiểm tra xem tin nhắn của người dùng có phải là một lời chào đơn giản hay không.
     * @param input Tin nhắn từ người dùng.
     * @return true nếu là lời chào, ngược lại là false.
     */
    private boolean isSimpleGreeting(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        // Chuẩn hóa input: chuyển về chữ thường và xóa khoảng trắng thừa
        String normalizedInput = input.toLowerCase().trim();
        return SIMPLE_GREETINGS.contains(normalizedInput);
    }


    private Agent chooseAgent(String userMessage, RagContext context) {
        String prompt = buildOrchestratorPrompt(userMessage);

        log.info("Choosing agent for query: '{}'", userMessage);

        // ✅ 4. Gọi LLM thông qua service routing mới, rẻ hơn
        Response<AiMessage> response = routingTrackedChatLanguageModel.generate(
                Collections.singletonList(new UserMessage(prompt)),
                context.getUser().getId(),
                context.getSession().getId()
        );
        
        String chosenAgentName = response.content().text().trim();
        log.info("Query: '{}' -> Routed to: {} (using routing model)", userMessage, chosenAgentName.toUpperCase());

        Agent chosenAgent = agents.get(chosenAgentName);

        if (chosenAgent == null) {
            log.warn("Could not find agent named '{}'. Falling back to default agent '{}'.",
                    chosenAgentName, defaultAgent.getName());
            chosenAgent = defaultAgent;
        }

        return chosenAgent;
    }
    
    // Phương thức này không thay đổi
    private String buildOrchestratorPrompt(String userInput) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là một hệ thống định tuyến AI chuyên nghiệp. Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và chọn một agent chuyên biệt phù hợp nhất để xử lý nó.\n");
        promptBuilder.append("Chỉ trả lời bằng tên của agent được chọn. KHÔNG thêm bất kỳ lời giải thích hay dấu câu nào.\n\n");
        promptBuilder.append("Các agent có sẵn:\n");

        for (Agent agent : agents.values()) {
            promptBuilder.append(String.format("- Tên: %s, Mô tả: %s\n", agent.getName(), agent.getDescription()));
        }

        promptBuilder.append("\nCâu hỏi của người dùng: \"").append(userInput).append("\"\n");
        promptBuilder.append("Tên agent được chọn: ");

        return promptBuilder.toString();
    }

    // Phương thức này không thay đổi
    private void saveConversationAndUpdateMemory(RagContext context, String userMessage, String assistantResponse) {
        try {
            chatMessageService.saveMessage(context.getSession(), "user", userMessage);
            chatMessageService.saveMessage(context.getSession(), "assistant", assistantResponse);
            log.info("Orchestrator saved conversation to DB for session {}", context.getSession().getId());

            context.getChatMemory().add(UserMessage.from(userMessage));
            context.getChatMemory().add(AiMessage.from(assistantResponse));
            log.info("Orchestrator updated short-term memory for session {}", context.getSession().getId());
        } catch (Exception e) {
            log.error("Error during centralized persistence in Orchestrator for session {}: {}", context.getSession().getId(), e.getMessage(), e);
        }
    }
}