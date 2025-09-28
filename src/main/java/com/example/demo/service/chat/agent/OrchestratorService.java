package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
// Sửa 1: Xóa import không còn được sử dụng
// import com.example.demo.service.chat.integration.RoutingTrackedChatLanguageModel; 
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryIntent;
import com.example.demo.service.chat.orchestration.rules.QueryIntentClassificationService;
import com.example.demo.service.chat.orchestration.rules.QueryRewriteService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel; // Sửa 2: Import interface
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier; // Sửa 3: Import @Qualifier
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    // --- CÁC DEPENDENCY ---
    // Sửa 4: Thay đổi kiểu dữ liệu của trường thành interface
    private final ChatLanguageModel routingTrackedChatLanguageModel;
    private final ChatMessageService chatMessageService;
    private final QuestionAnswerCacheService cacheService;
    private final Map<String, Agent> agents;
    private final Agent defaultAgent;
    private final FinancialAnalystAgent financialAnalystAgent;
    private final QueryIntentClassificationService queryIntentClassificationService;
    private final FollowUpQueryDetectionService followUpQueryDetectionService;
    private final QueryRewriteService queryRewriteService;

    @Autowired
    // Sửa 5: Cập nhật constructor để inject đúng bean
    public OrchestratorService(List<Agent> agentList,
                               @Qualifier("routingModel") ChatLanguageModel routingTrackedChatLanguageModel,
                               ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService,
                               FinancialAnalystAgent financialAnalystAgent,
                               QueryIntentClassificationService queryIntentClassificationService,
                               FollowUpQueryDetectionService followUpQueryDetectionService,
                               QueryRewriteService queryRewriteService) {
        this.chatMessageService = chatMessageService;
        this.routingTrackedChatLanguageModel = routingTrackedChatLanguageModel;
        this.cacheService = cacheService;
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.defaultAgent = this.agents.get("RAGAgent");
        this.financialAnalystAgent = financialAnalystAgent;
        this.queryIntentClassificationService = queryIntentClassificationService;
        this.followUpQueryDetectionService = followUpQueryDetectionService;
        this.queryRewriteService = queryRewriteService;

        if (this.agents.get("RAGAgent") == null || this.agents.get("ChitChatAgent") == null || this.agents.get("MemoryQueryAgent") == null || this.agents.get("ToolAgent") == null) {
            throw new IllegalStateException("One or more required agents (RAGAgent, ChitChatAgent, MemoryQueryAgent, ToolAgent) not found!");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    // --- CÁC PHƯƠNG THỨC CÒN LẠI GIỮ NGUYÊN ---
    // (Bao gồm orchestrate, chooseAgent, v.v... đã đúng logic)
    private boolean isUnhelpfulAnswer(String reply) {
        if (reply == null || reply.isBlank()) {
            return true;
        }
        String lowerCaseReply = reply.toLowerCase();
        return lowerCaseReply.contains("không tìm thấy") ||
               lowerCaseReply.contains("không có trong cơ sở kiến thức") ||
               lowerCaseReply.contains("không có trong tài liệu") ||
               lowerCaseReply.contains("tôi không thể giúp");
    }

 // ✅ PHƯƠNG THỨC ORCHESTRATE ĐÃ ĐƯỢC NÂNG CẤP HOÀN TOÀN
    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {

        // --- BƯỚC 1: XỬ LÝ VÀ LÀM GIÀU TRUY VẤN ---
        List<ChatMessage> chatHistory = context.getChatMemory().messages();
        String rewrittenUserMessage = queryRewriteService.rewrite(chatHistory, userMessage);
        context.setQuery(rewrittenUserMessage);

        QueryIntent intent = queryIntentClassificationService.classify(rewrittenUserMessage);
        log.info("Classified intent as: {} for session {}", intent, context.getSession().getId());

        // --- BƯỚC 2: LOGIC ĐIỀU PHỐI THÔNG MINH ---
        Agent chosenAgent;
        String finalAnswer;

        // Ưu tiên 1: Nếu là câu hỏi động, đi thẳng đến ToolAgent
        if (intent == QueryIntent.DYNAMIC_QUERY) {
            log.info("Intent is DYNAMIC_QUERY, routing directly to ToolAgent.");
            chosenAgent = agents.get("ToolAgent");
            chosenAgent.execute(context);
            finalAnswer = context.getReply();
        } else {
            // Ưu tiên 2: Kiểm tra cache cho các loại câu hỏi khác
            if (!regenerate) {
                String contextForLookup = determineContextForLookup(rewrittenUserMessage, getLastBotMessage(chatHistory));
                Optional<String> cachedAnswer = cacheService.findCachedAnswer(rewrittenUserMessage, contextForLookup);
                if (cachedAnswer.isPresent()) {
                    log.info("Cache hit for session {}. Returning cached answer.", context.getSession().getId());
                    String answerFromCache = cachedAnswer.get();
                    saveConversationAndUpdateMemory(context, userMessage, answerFromCache);
                    return answerFromCache;
                }
            }
            log.info("Cache miss or regenerate request for session {}.", context.getSession().getId());

            // Nếu không có trong cache, chọn agent phù hợp (RAG, ChitChat...)
            chosenAgent = chooseAgent(rewrittenUserMessage, context);
            chosenAgent.execute(context);
            finalAnswer = context.getReply();
        }

        // --- BƯỚC 3: FALLBACK TỰ ĐỘNG TỪ RAG SANG TOOL ---
        // Nếu agent ban đầu là RAG và trả lời không hữu ích, tự động thử lại bằng ToolAgent
        if (isUnhelpfulAnswer(finalAnswer) && "RAGAgent".equals(chosenAgent.getName())) {
            log.warn("RAGAgent returned an unhelpful answer. Triggering AUTOMATIC fallback to ToolAgent.");
            Agent toolAgent = agents.get("ToolAgent");
            if (toolAgent != null) {
                toolAgent.execute(context); // Thực thi lại với cùng context
                String fallbackAnswer = context.getReply();
                if (fallbackAnswer != null && !fallbackAnswer.isBlank() && !isUnhelpfulAnswer(fallbackAnswer)) {
                    finalAnswer = fallbackAnswer; // Ghi đè câu trả lời
                    chosenAgent = toolAgent; // Cập nhật lại agent đã cung cấp câu trả lời cuối cùng
                    log.info("Fallback to ToolAgent successful. Using its answer.");
                } else {
                    log.warn("Fallback ToolAgent also returned an unhelpful answer.");
                }
            }
        }

        // --- BƯỚC 4: LƯU CACHE VỚI TTL ĐỘNG VÀ LƯU LỊCH SỬ ---
        if (finalAnswer != null && !finalAnswer.isEmpty() && !isUnhelpfulAnswer(finalAnswer)) {
            Map<String, Object> metadata = new HashMap<>();
            ZonedDateTime validUntil;
            String contextForLookup = determineContextForLookup(rewrittenUserMessage, getLastBotMessage(chatHistory));

            // Logic TTL động: DYNAMIC_QUERY hoặc ToolAgent sẽ có TTL ngắn
            if (intent == QueryIntent.DYNAMIC_QUERY || "ToolAgent".equals(chosenAgent.getName())) {
                metadata.put("source", "tool_api");
                validUntil = ZonedDateTime.now().plusHours(1); // Cache thông tin động trong 1 giờ
                log.info("Saving DYNAMIC answer from agent '{}' to cache with 1-hour TTL.", chosenAgent.getName());
            } else {
                switch (intent) {
                    case CHIT_CHAT:
                        metadata.put("source", "llm_chit_chat");
                        validUntil = ZonedDateTime.now().plusMonths(1);
                        break;
                    case RAG_QUERY:
                    case STATIC_QUERY:
                    default:
                        metadata.put("source", "llm_static_knowledge");
                        validUntil = ZonedDateTime.now().plusYears(1);
                        break;
                }
                log.info("Saving STATIC answer from agent '{}' to cache.", chosenAgent.getName());
            }
            metadata.put("chosen_agent", chosenAgent.getName());
            cacheService.saveToCache(rewrittenUserMessage, finalAnswer, contextForLookup, metadata, validUntil);
            
            saveConversationAndUpdateMemory(context, userMessage, finalAnswer);
        } else if (finalAnswer != null && !finalAnswer.isEmpty()) {
             // Vẫn lưu cuộc trò chuyện dù câu trả lời không hữu ích
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
        log.info("Choosing agent for rewritten query: '{}'", userMessage);
        
        Response<AiMessage> response = routingTrackedChatLanguageModel.generate(
                Collections.singletonList(new UserMessage(prompt))
        );

        String chosenAgentName = response.content().text().trim();
        log.info("Rewritten Query: '{}' -> Routed to: {} (using routing model)", userMessage, chosenAgentName.toUpperCase());
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
        promptBuilder.append("You are an expert AI routing system. Your task is to analyze the user's question and select the most suitable specialist agent to handle it.\n");
        promptBuilder.append("RULES:\n");
        promptBuilder.append("1. For questions about real-time, volatile data (weather, time, stock prices), or general knowledge questions about public figures, events, or facts (e.g., 'Who is the prime minister of Japan?'), you MUST choose 'ToolAgent'.\n");
        promptBuilder.append("2. For questions about internal documents, user-specific data, or information explicitly stored in the system's knowledge base, choose 'RAGAgent'.\n");
        promptBuilder.append("3. For casual conversation, greetings, or non-factual questions, choose 'ChitChatAgent'.\n");
        promptBuilder.append("Your output MUST ONLY be the name of the chosen agent. NO explanations or punctuation.\n\n");

        promptBuilder.append("Available Agents:\n");
        for (Agent agent : agents.values()) {
            promptBuilder.append(String.format("- Name: %s, Description: %s\n", agent.getName(), agent.getDescription()));
        }
        promptBuilder.append("\nUser Question: \"").append(userInput).append("\"\n");
        promptBuilder.append("Chosen Agent Name: ");
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