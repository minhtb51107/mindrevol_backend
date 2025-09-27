package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService; // ✅ 1. IMPORT
import com.example.demo.service.chat.integration.RoutingTrackedChatLanguageModel;
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    private final RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel;
    private final ChatMessageService chatMessageService;
    private final QuestionAnswerCacheService cacheService; // ✅ 2. THÊM DEPENDENCY
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;

    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "xin chào", "chào bạn", "chào", "helo", "alo"
    ));

    @Autowired
    public OrchestratorService(List<Agent> agentList,
                               RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel,
                               ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService) { // ✅ 3. INJECT QUA CONSTRUCTOR
        this.chatMessageService = chatMessageService;
        this.routingTrackedChatLanguageModel = routingTrackedChatLanguageModel;
        this.cacheService = cacheService; // ✅ 3. KHỞI TẠO
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.defaultAgent = this.agents.get("RAGAgent");

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    // ✅ 4. SỬA SIGNATURE CỦA PHƯƠNG THỨC ĐỂ NHẬN `regenerate`
    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        
        // --- LOGIC CACHING ---
    	// ✅ 1. LẤY TIN NHẮN CUỐI CÙNG TỪ BỘ NHỚ
        String lastBotMessage = getLastBotMessage(context.getChatMemory().messages());

        // --- LOGIC CACHING (ĐÃ CẬP NHẬT) ---
        if (!regenerate) {
            // ✅ 2. TRUYỀN lastBotMessage VÀO HÀM TÌM KIẾM
            Optional<String> cachedAnswer = cacheService.findCachedAnswer(userMessage, lastBotMessage);
            if (cachedAnswer.isPresent()) {
                log.info("Cache hit for session {}. Trả về câu trả lời từ cache.", context.getSession().getId());
                String answerFromCache = cachedAnswer.get();
                saveConversationAndUpdateMemory(context, userMessage, answerFromCache);
                return answerFromCache;
            }
        }
        log.info("Cache miss or regenerate request for session {}.", context.getSession().getId());
        // --- KẾT THÚC LOGIC CACHING ---

        
        // --- LUỒNG XỬ LÝ HIỆN TẠI ---
        if (isSimpleGreeting(userMessage)) {
            log.info("Simple greeting detected. Routing directly to ChitChatAgent.");
            Agent chitChatAgent = agents.get("ChitChatAgent");
            if (chitChatAgent != null) {
                chitChatAgent.execute(context);
                String response = context.getReply();
                if (response != null && !response.isEmpty()) {
                    // Không cần lưu cache cho câu chào hỏi đơn giản
                    saveConversationAndUpdateMemory(context, userMessage, response);
                }
                return response;
            }
        }
        
        Agent chosenAgent = chooseAgent(userMessage, context);
        chosenAgent.execute(context);
        String newAnswer = context.getReply();

        if (newAnswer != null && !newAnswer.isEmpty()) {
            // ✅ 3. TRUYỀN lastBotMessage VÀO HÀM LƯU
            cacheService.saveToCache(userMessage, newAnswer, lastBotMessage);
            saveConversationAndUpdateMemory(context, userMessage, newAnswer);
        }

        return newAnswer;
    }

    // Phương thức `orchestrate` cũ không còn `regenerate` sẽ gây lỗi,
    // ta tạo một phiên bản overload để tương thích ngược (hoặc xóa đi nếu không cần).
    public String orchestrate(String userMessage, RagContext context) {
        return this.orchestrate(userMessage, context, false);
    }
    

    /**
     * ✅ 4. THÊM PHƯƠNG THỨC HELPER ĐỂ LẤY TIN NHẮN CUỐI CÙNG CỦA BOT
     * Lấy tin nhắn cuối cùng trong lịch sử, nếu là của AI thì trả về nội dung.
     * @param messages Lịch sử cuộc trò chuyện
     * @return Nội dung tin nhắn cuối cùng của AI, hoặc null nếu không có.
     */
    private String getLastBotMessage(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        // Lấy tin nhắn cuối cùng
        ChatMessage lastMessage = messages.get(messages.size() - 1);

        // Kiểm tra xem có phải là tin nhắn của AI không
        if (lastMessage instanceof AiMessage) {
            return lastMessage.text();
        }

        return null;
    }


    private boolean isSimpleGreeting(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String normalizedInput = input.toLowerCase().trim();
        return SIMPLE_GREETINGS.contains(normalizedInput);
    }

    private Agent chooseAgent(String userMessage, RagContext context) {
        String prompt = buildOrchestratorPrompt(userMessage);
        log.info("Choosing agent for query: '{}'", userMessage);

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
    
    private String buildOrchestratorPrompt(String userInput) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là một hệ thống định tuyến AI chuyên nghiệp. Nhiệm vụ của bạn là phân tích câu hỏi của người dùng và chọn một agent chuyên biệt phù hợp nhất để xử lý nó.\n");
     // ✅ THÊM HƯỚNG DẪN MỚI
        promptBuilder.append("QUAN TRỌNG: Nếu câu hỏi liên quan đến dữ liệu thay đổi liên tục như thời tiết, thời gian, hoặc giá cổ phiếu, hãy ưu tiên chọn agent có khả năng sử dụng công cụ (ToolAgent).\n");
        promptBuilder.append("Chỉ trả lời bằng tên của agent được chọn. KHÔNG thêm bất kỳ lời giải thích hay dấu câu nào.\n\n");
        promptBuilder.append("Các agent có sẵn:\n");

        for (Agent agent : agents.values()) {
            promptBuilder.append(String.format("- Tên: %s, Mô tả: %s\n", agent.getName(), agent.getDescription()));
        }

        promptBuilder.append("\nCâu hỏi của người dùng: \"").append(userInput).append("\"\n");
        promptBuilder.append("Tên agent được chọn: ");

        return promptBuilder.toString();
    }

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