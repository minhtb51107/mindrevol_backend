package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryIntent;
import com.example.demo.service.chat.orchestration.rules.QueryIntentClassificationService;
import com.example.demo.service.chat.orchestration.rules.QueryRewriteService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    // --- CÁC DEPENDENCY ---
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

    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        // ✅ --- BƯỚC 1: XỬ LÝ VÀ LÀM GIÀU TRUY VẤN (ĐÃ TỐI ƯU HÓA) ---
        List<ChatMessage> chatHistory = context.getChatMemory().messages();
        
        QueryIntent initialIntent = queryIntentClassificationService.classify(userMessage);
        log.info("Initial classified intent (pre-rewrite): {} for session {}", initialIntent, context.getSession().getId());

        String rewrittenUserMessage = userMessage;
        if (initialIntent == QueryIntent.RAG_QUERY || followUpQueryDetectionService.isFollowUp(userMessage)) {
            log.info("Intent may require context. Rewriting query for session {}.", context.getSession().getId());
            rewrittenUserMessage = queryRewriteService.rewrite(chatHistory, userMessage);
        }
        context.setQuery(rewrittenUserMessage);

        QueryIntent finalIntent = (rewrittenUserMessage.equals(userMessage)) 
                                    ? initialIntent 
                                    : queryIntentClassificationService.classify(rewrittenUserMessage);
        log.info("Final classified intent: {} for session {}", finalIntent, context.getSession().getId());

        // ✅ --- BƯỚC 2: LOGIC ĐIỀU PHỐI THÔNG MINH DỰA TRÊN Ý ĐỊNH ---
        Agent chosenAgent;
        String finalAnswer;

        // Ưu tiên 1: Xử lý các câu hỏi không cần RAG trước
        switch (finalIntent) {
            case DYNAMIC_QUERY:
                log.info("Intent is DYNAMIC_QUERY, routing directly to ToolAgent.");
                chosenAgent = agents.get("ToolAgent");
                chosenAgent.execute(context);
                finalAnswer = context.getReply();
                return handleCachingAndPersistence(userMessage, rewrittenUserMessage, finalAnswer, context, chosenAgent, finalIntent);

            case CHIT_CHAT:
                log.info("Intent is CHIT_CHAT, routing directly to ChitChatAgent.");
                chosenAgent = agents.get("ChitChatAgent");
                chosenAgent.execute(context);
                finalAnswer = context.getReply();
                return handleCachingAndPersistence(userMessage, rewrittenUserMessage, finalAnswer, context, chosenAgent, finalIntent);

            case MEMORY_QUERY:
                 log.info("Intent is MEMORY_QUERY, routing directly to MemoryQueryAgent.");
                 chosenAgent = agents.get("MemoryQueryAgent");
                 chosenAgent.execute(context);
                 finalAnswer = context.getReply();
                 return handleCachingAndPersistence(userMessage, rewrittenUserMessage, finalAnswer, context, chosenAgent, finalIntent);
        }
        
        // --- LUỒNG XỬ LÝ MẶC ĐỊNH CHO RAG_QUERY VÀ STATIC_QUERY ---
        log.info("Intent is RAG_QUERY or STATIC_QUERY, proceeding with cache check and RAG pipeline.");

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

        chosenAgent = chooseAgent(rewrittenUserMessage, context);
        chosenAgent.execute(context);
        finalAnswer = context.getReply();

        // Fallback Logic
        if (isUnhelpfulAnswer(finalAnswer) && "RAGAgent".equals(chosenAgent.getName())) {
            log.warn("RAGAgent returned an unhelpful answer. Triggering AUTOMATIC fallback to ToolAgent.");
            Agent toolAgent = agents.get("ToolAgent");
            if (toolAgent != null) {
                toolAgent.execute(context);
                String fallbackAnswer = context.getReply();
                if (fallbackAnswer != null && !fallbackAnswer.isBlank() && !isUnhelpfulAnswer(fallbackAnswer)) {
                    finalAnswer = fallbackAnswer;
                    chosenAgent = toolAgent;
                    log.info("Fallback to ToolAgent successful. Using its answer.");
                } else {
                    log.warn("Fallback ToolAgent also returned an unhelpful answer.");
                }
            }
        }
        
        return handleCachingAndPersistence(userMessage, rewrittenUserMessage, finalAnswer, context, chosenAgent, finalIntent);
    }

    private String handleCachingAndPersistence(String originalUserMessage, String rewrittenUserMessage, String finalAnswer, RagContext context, Agent chosenAgent, QueryIntent intent) {
        if (finalAnswer != null && !finalAnswer.isEmpty() && !isUnhelpfulAnswer(finalAnswer)) {
            Map<String, Object> metadata = new HashMap<>();
            ZonedDateTime validUntil;
            List<ChatMessage> chatHistory = context.getChatMemory().messages();
            String contextForLookup = determineContextForLookup(rewrittenUserMessage, getLastBotMessage(chatHistory));

            if (intent == QueryIntent.DYNAMIC_QUERY || "ToolAgent".equals(chosenAgent.getName())) {
                metadata.put("source", "tool_api");
                validUntil = ZonedDateTime.now().plusHours(1);
                log.info("Saving DYNAMIC answer from agent '{}' to cache with 1-hour TTL.", chosenAgent.getName());
            } else {
                switch (intent) {
                    case CHIT_CHAT:
                        metadata.put("source", "llm_chit_chat");
                        validUntil = ZonedDateTime.now().plusMonths(1);
                        break;
                    case MEMORY_QUERY:
                        metadata.put("source", "llm_memory");
                         validUntil = ZonedDateTime.now().plusDays(1);
                        break;
                    case RAG_QUERY:
                    case STATIC_QUERY:
                    default:
                        metadata.put("source", "llm_static_knowledge");
                        validUntil = ZonedDateTime.now().plusYears(1);
                        break;
                }
                log.info("Saving answer from agent '{}' to cache.", chosenAgent.getName());
            }
            metadata.put("chosen_agent", chosenAgent.getName());
            cacheService.saveToCache(rewrittenUserMessage, finalAnswer, contextForLookup, metadata, validUntil);
        }
        
        // Luôn lưu lịch sử hội thoại, dù câu trả lời có hữu ích hay không
        saveConversationAndUpdateMemory(context, originalUserMessage, finalAnswer);

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
            context.getChatMemory().add(AiMessage.from(assistantResponse == null ? "" : assistantResponse));
            log.info("Orchestrator updated short-term memory for session {}", context.getSession().getId());
        } catch (Exception e) {
            log.error("Error during centralized persistence in Orchestrator for session {}: {}", context.getSession().getId(), e.getMessage(), e);
        }
    }
}