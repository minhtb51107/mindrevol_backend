package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
import com.example.demo.service.chat.integration.RoutingTrackedChatLanguageModel;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryIntent;
import com.example.demo.service.chat.orchestration.rules.QueryIntentClassificationService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    // --- CÁC DEPENDENCY HIỆN CÓ ---
    private final RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel;
    private final ChatMessageService chatMessageService;
    private final QuestionAnswerCacheService cacheService;
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;
    private final FinancialAnalystAgent financialAnalystAgent;
    private final QueryIntentClassificationService queryIntentClassificationService;
    private final FollowUpQueryDetectionService followUpQueryDetectionService;

    private static final Set<String> SIMPLE_GREETINGS = new HashSet<>(Arrays.asList(
            "hi", "hello", "xin chào", "chào bạn", "chào", "helo", "alo"
    ));

    @Autowired
    public OrchestratorService(List<Agent> agentList,
                               RoutingTrackedChatLanguageModel routingTrackedChatLanguageModel,
                               ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService,
                               FinancialAnalystAgent financialAnalystAgent,
                               QueryIntentClassificationService queryIntentClassificationService,
                               FollowUpQueryDetectionService followUpQueryDetectionService) {
        this.chatMessageService = chatMessageService;
        this.routingTrackedChatLanguageModel = routingTrackedChatLanguageModel;
        this.cacheService = cacheService;
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.defaultAgent = this.agents.get("RAGAgent");
        this.financialAnalystAgent = financialAnalystAgent;
        this.queryIntentClassificationService = queryIntentClassificationService;
        this.followUpQueryDetectionService = followUpQueryDetectionService;

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }
    
    /**
     * Phương thức này giờ đây đóng vai trò kép:
     * 1. Kích hoạt "Kế hoạch B" (fallback).
     * 2. Là "Bức tường lửa" để bảo vệ cache.
     */
    private boolean isUnhelpfulAnswer(String reply) {
        if (reply == null || reply.isBlank()) {
            return true; // Câu trả lời rỗng cũng là vô ích
        }
        String lowerCaseReply = reply.toLowerCase();
        // Thêm bất kỳ cụm từ nào cho thấy câu trả lời là vô ích vào đây
        return lowerCaseReply.contains("không tìm thấy") ||
               lowerCaseReply.contains("không có trong cơ sở kiến thức") ||
               lowerCaseReply.contains("không có trong tài liệu") ||
               lowerCaseReply.contains("tôi không thể giúp");
    }
    
    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        
        // BƯỚC 1 & 2: PHÂN LOẠI INTENT VÀ XỬ LÝ DYNAMIC QUERY
        QueryIntent intent = queryIntentClassificationService.classify(userMessage);
        log.info("Classified intent as: {} for session {}", intent, context.getSession().getId());

        if (intent == QueryIntent.DYNAMIC_QUERY) {
            log.info("Dynamic query detected. Directly executing ToolAgent and bypassing cache.");
            Agent toolAgent = agents.get("ToolAgent");
            toolAgent.execute(context);
            String toolAnswer = context.getReply();
            if (toolAnswer != null && !toolAnswer.isEmpty()) {
                saveConversationAndUpdateMemory(context, userMessage, toolAnswer);
            }
            return toolAnswer;
        }

        // BƯỚC 3: KIỂM TRA CACHE
        String lastBotMessage = getLastBotMessage(context.getChatMemory().messages());
        String contextForLookup = determineContextForLookup(userMessage, lastBotMessage);

        if (!regenerate) {
            Optional<String> cachedAnswer = cacheService.findCachedAnswer(userMessage, contextForLookup);
            if (cachedAnswer.isPresent()) {
                log.info("Cache hit for session {}. Returning cached answer.", context.getSession().getId());
                String answerFromCache = cachedAnswer.get();
                saveConversationAndUpdateMemory(context, userMessage, answerFromCache);
                return answerFromCache;
            }
        }
        log.info("Cache miss or regenerate request for session {}.", context.getSession().getId());

        // BƯỚC 4: ĐỊNH TUYẾN VÀ KẾ HOẠCH B
        Agent chosenAgent = chooseAgent(userMessage, context);
        chosenAgent.execute(context);
        String primaryAnswer = context.getReply();

        String finalAnswer = primaryAnswer;

        if (isUnhelpfulAnswer(primaryAnswer)) {
            log.warn("Agent {} returned an unhelpful answer. Triggering fallback to ToolAgent.", chosenAgent.getName());
            Agent toolAgent = agents.get("ToolAgent");
            if (toolAgent != null) {
                toolAgent.execute(context);
                finalAnswer = context.getReply(); // Câu trả lời cuối cùng là từ ToolAgent
            }
        }
        
        // BƯỚC 5: LƯU TRỮ VÀ TRẢ VỀ
        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            // Chỉ lưu vào cache nếu câu trả lời ban đầu (primaryAnswer) là hữu ích.
            // Điều này ngăn việc lưu kết quả từ ToolAgent (kế hoạch B) vào cache.
            if (!isUnhelpfulAnswer(primaryAnswer)) {
                 Map<String, Object> metadata = new HashMap<>();
                 ZonedDateTime validUntil;
                 switch (intent) {
                     case RAG_QUERY:
                     case STATIC_QUERY:
                     default:
                        metadata.put("source", "llm_static_knowledge");
                        metadata.put("chosen_agent", chosenAgent.getName());
                        validUntil = ZonedDateTime.now().plusYears(1);
                        break;
                    case CHIT_CHAT:
                        metadata.put("source", "llm_chit_chat");
                        metadata.put("chosen_agent", chosenAgent.getName());
                        validUntil = ZonedDateTime.now().plusMonths(1);
                        break;
                 }
                cacheService.saveToCache(userMessage, primaryAnswer, contextForLookup, metadata, validUntil);
            }
            
            saveConversationAndUpdateMemory(context, userMessage, finalAnswer);
        }

        return finalAnswer;
    }

    private String determineContextForLookup(String userMessage, String lastBotMessage) {
        if (lastBotMessage != null && !lastBotMessage.isBlank() && followUpQueryDetectionService.isFollowUp(userMessage)) {
            return lastBotMessage;
        }
        return null;
    }

    // ... (tất cả các phương thức helper khác không thay đổi) ...
    public String orchestrate(String userMessage, RagContext context) {
        return this.orchestrate(userMessage, context, false);
    }
    
    private ChatMessage findLastAiMessage(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof AiMessage) return history.get(i);
        }
        return null;
    }
    
    private String getLastBotMessage(List<ChatMessage> messages) {
        ChatMessage lastAiMessage = findLastAiMessage(messages);
        return (lastAiMessage != null) ? lastAiMessage.text() : null;
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