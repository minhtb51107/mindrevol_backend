package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
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
    private final QuestionAnswerCacheService cacheService;
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;
    
    // ✅ 1. THÊM DEPENDENCY CHO AGENT PHÂN TÍCH
    private final FinancialAnalystAgent financialAnalystAgent;

    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "xin chào", "chào bạn", "chào", "helo", "alo"
    ));

    @Autowired
    public OrchestratorService(List<Agent> agentList,
                               RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel,
                               ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService,
                               FinancialAnalystAgent financialAnalystAgent) { // ✅ 2. INJECT AGENT MỚI
        this.chatMessageService = chatMessageService;
        this.routingTrackedChatLanguageModel = routingTrackedChatLanguageModel;
        this.cacheService = cacheService;
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.defaultAgent = this.agents.get("RAGAgent");
        
        // ✅ 2. KHỞI TẠO AGENT MỚI
        this.financialAnalystAgent = financialAnalystAgent;

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }
    
    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        
        String lastBotMessage = getLastBotMessage(context.getChatMemory().messages());

        if (!regenerate) {
            Optional<String> cachedAnswer = cacheService.findCachedAnswer(userMessage, lastBotMessage);
            if (cachedAnswer.isPresent()) {
                log.info("Cache hit for session {}. Trả về câu trả lời từ cache.", context.getSession().getId());
                String answerFromCache = cachedAnswer.get();
                saveConversationAndUpdateMemory(context, userMessage, answerFromCache);
                return answerFromCache;
            }
        }
        log.info("Cache miss or regenerate request for session {}.", context.getSession().getId());
        
        // ✅ 3. TRIỂN KHAI LOGIC PHÂN TÍCH NỐI TIẾP
//        List<ChatMessage> history = context.getChatMemory().messages();
//        if (isFollowUpAnalysisQuestion(userMessage) && !history.isEmpty()) {
//            ChatMessage lastAiMessage = findLastAiMessage(history);
//            
//            if (lastAiMessage != null && isStockData(lastAiMessage.text())) {
//                log.info("Phát hiện câu hỏi phân tích nối tiếp. Kích hoạt FinancialAnalystAgent.");
//                
//                String analysisRequest = String.format(
//                    "Câu hỏi của người dùng: '%s'. Dữ liệu chứng khoán để phân tích: '%s'",
//                    userMessage,
//                    lastAiMessage.text()
//                );
//
//                String analysisResult = financialAnalystAgent.analyzeStockData(analysisRequest);
//                
//                context.setReply(analysisResult);
//                saveConversationAndUpdateMemory(context, userMessage, analysisResult);
//                cacheService.saveToCache(userMessage, analysisResult, lastBotMessage);
//                return analysisResult;
//            }
//        }
        
        // --- LUỒNG XỬ LÝ ĐỊNH TUYẾN CŨ ---
        if (isSimpleGreeting(userMessage)) {
            log.info("Simple greeting detected. Routing directly to ChitChatAgent.");
            Agent chitChatAgent = agents.get("ChitChatAgent");
            if (chitChatAgent != null) {
                chitChatAgent.execute(context);
                String response = context.getReply();
                if (response != null && !response.isEmpty()) {
                    saveConversationAndUpdateMemory(context, userMessage, response);
                }
                return response;
            }
        }
        
        Agent chosenAgent = chooseAgent(userMessage, context);
        chosenAgent.execute(context);
        String newAnswer = context.getReply();

        if (newAnswer != null && !newAnswer.isEmpty()) {
            cacheService.saveToCache(userMessage, newAnswer, lastBotMessage);
            saveConversationAndUpdateMemory(context, userMessage, newAnswer);
        }

        return newAnswer;
    }

    public String orchestrate(String userMessage, RagContext context) {
        return this.orchestrate(userMessage, context, false);
    }
    
    // ✅ 4. THÊM CÁC PHƯƠNG THỨC HELPER CHO LOGIC MỚI

    /**
     * Kiểm tra xem câu hỏi có phải là một yêu cầu phân tích dữ liệu đã cho trước đó không.
     */
    private boolean isFollowUpAnalysisQuestion(String query) {
        if (query == null) return false;
        String lowerCaseQuery = query.toLowerCase();
        return lowerCaseQuery.contains("dựa trên") ||
               lowerCaseQuery.contains("cho thấy điều gì") ||
               lowerCaseQuery.contains("nghĩa là gì") ||
               lowerCaseQuery.contains("phân tích");
    }

    /**
     * Kiểm tra xem một chuỗi văn bản có chứa dữ liệu chứng khoán hay không.
     */
    private boolean isStockData(String text) {
        if (text == null) return false;
        String lowerCaseText = text.toLowerCase();
        return lowerCaseText.contains("giá hiện tại là") ||
               lowerCaseText.contains("thay đổi trong ngày");
    }

    /**
     * Tìm tin nhắn cuối cùng được gửi bởi AI (assistant) trong lịch sử hội thoại.
     */
    private ChatMessage findLastAiMessage(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage message = history.get(i);
            // Sử dụng instanceof để kiểm tra kiểu tin nhắn một cách an toàn
            if (message instanceof AiMessage) {
                return message;
            }
        }
        return null;
    }
    
    // --- CÁC PHƯƠNG THỨC CŨ VẪN GIỮ NGUYÊN ---

    private String getLastBotMessage(List<ChatMessage> messages) {
        ChatMessage lastAiMessage = findLastAiMessage(messages);
        return (lastAiMessage != null) ? lastAiMessage.text() : null;
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