package com.example.demo.service.chat.agent;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier; // ✅ THÊM IMPORT
import org.springframework.stereotype.Service;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QueryPreProcessingService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
import com.example.demo.service.chat.agent.tools.ChitChatService;
import com.example.demo.service.chat.agent.tools.MemoryQueryService;
import com.example.demo.service.chat.agent.tools.RAGService;
import com.example.demo.service.chat.guardrail.GuardrailManager;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryComplexity; // ✅ THÊM IMPORT
import com.example.demo.service.chat.orchestration.rules.QueryComplexityAnalysisService; // ✅ THÊM IMPORT
import com.example.demo.service.chat.orchestration.rules.QueryIntent;
import com.example.demo.service.chat.orchestration.rules.QueryIntentClassificationService;
import com.example.demo.service.chat.orchestration.rules.QueryRewriteService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrchestratorService {

    // --- Các dependency hiện có ---
    private final ChatMessageService chatMessageService;
    private final QuestionAnswerCacheService cacheService;
    private final FollowUpQueryDetectionService followUpQueryDetectionService;
    private final QueryRewriteService queryRewriteService;
    private final QueryIntentClassificationService intentClassifier;
    private final RAGService ragService;
    private final ChitChatService chitChatService;
    private final MemoryQueryService memoryQueryService;
    private final GuardrailManager guardrailManager;
    private final QueryPreProcessingService queryPreProcessingService;

    // --- ✅ THÊM CÁC DEPENDENCY MỚI CHO LOGIC ĐỊNH TUYẾN THÔNG MINH ---
    private final QueryComplexityAnalysisService complexityAnalyzer;
    private final ToolUsingAgent simpleRouterAgent;
    private final ToolUsingAgent advancedRouterAgent; // Thay thế cho RouterAgent cũ

    @Autowired
    public OrchestratorService(ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService,
                               FollowUpQueryDetectionService followUpQueryDetectionService,
                               QueryRewriteService queryRewriteService,
                               QueryIntentClassificationService intentClassifier,
                               // 🛑 RouterAgent cũ không còn được inject trực tiếp
                               RAGService ragService,
                               ChitChatService chitChatService,
                               MemoryQueryService memoryQueryService,
                               QueryPreProcessingService queryPreProcessingService,
                               GuardrailManager guardrailManager,
                               // ✅ INJECT CÁC BEAN MỚI
                               QueryComplexityAnalysisService complexityAnalyzer,
                               @Qualifier("simpleRouterAgent") ToolUsingAgent simpleRouterAgent,
                               @Qualifier("advancedRouterAgent") ToolUsingAgent advancedRouterAgent) {
        this.chatMessageService = chatMessageService;
        this.cacheService = cacheService;
        this.followUpQueryDetectionService = followUpQueryDetectionService;
        this.queryRewriteService = queryRewriteService;
        this.intentClassifier = intentClassifier;
        this.ragService = ragService;
        this.chitChatService = chitChatService;
        this.memoryQueryService = memoryQueryService;
        this.guardrailManager = guardrailManager;
        this.queryPreProcessingService = queryPreProcessingService;
        
        // ✅ Gán các dependency mới
        this.complexityAnalyzer = complexityAnalyzer;
        this.simpleRouterAgent = simpleRouterAgent;
        this.advancedRouterAgent = advancedRouterAgent;
        
        log.info("Orchestrator initialized with cost-optimized Tiered Routing architecture.");
    }
    
    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        String sanitizedUserMessage = guardrailManager.checkInput(userMessage);
        if (!sanitizedUserMessage.equals(userMessage)) {
            saveConversationAndUpdateMemory(context, userMessage, sanitizedUserMessage);
            return sanitizedUserMessage;
        }

        String processedQuery = queryPreProcessingService.process(sanitizedUserMessage);
        List<ChatMessage> chatHistory = context.getChatMemory().messages();
        String rewrittenUserMessage = processedQuery; // Bắt đầu với query đã được xử lý
        
        QueryIntent intent = intentClassifier.classify(processedQuery);
        context.setIntent(intent);
        log.info("Classified intent for session {}: {}", context.getSession().getId(), intent);
        
        if (intent != QueryIntent.CHIT_CHAT) {
            log.debug("Intent is not CHIT_CHAT, proceeding with follow-up detection and query rewrite.");
            if (followUpQueryDetectionService.isFollowUp(rewrittenUserMessage)) {
                log.info("Follow-up query detected. Rewriting query for session {}", context.getSession().getId());
                rewrittenUserMessage = queryRewriteService.rewrite(chatHistory, rewrittenUserMessage);
            }
        } else {
            log.debug("Intent is CHIT_CHAT, skipping follow-up and rewrite steps to save costs.");
        }
        context.setQuery(rewrittenUserMessage);
        
        if (!regenerate) {
            String contextForLookup = determineContextForLookup(rewrittenUserMessage, getLastBotMessage(chatHistory));
            Optional<String> cachedAnswer = cacheService.findCachedAnswer(rewrittenUserMessage, contextForLookup);
            if (cachedAnswer.isPresent()) {
                log.info("Cache hit for session {}. Returning cached answer.", context.getSession().getId());
                String answerFromCache = cachedAnswer.get();
                String sanitizedAnswer = guardrailManager.checkOutput(answerFromCache);
                saveConversationAndUpdateMemory(context, userMessage, sanitizedAnswer);
                return sanitizedAnswer;
            }
        }
        log.info("Cache miss or regenerate request for session {}.", context.getSession().getId());

        String agentResponse;
        String chosenAgentName = "unknown";

        switch (intent) {
            case CHIT_CHAT:
                chosenAgentName = "ChitChatService";
                agentResponse = chitChatService.chitChat(context); 
                break;
            case RAG_QUERY:
            case STATIC_QUERY:
                chosenAgentName = "RAGService";
                agentResponse = ragService.answerFromDocuments(rewrittenUserMessage, context.getSession().getId());
                break;
            case MEMORY_QUERY:
                chosenAgentName = "MemoryQueryService";
                agentResponse = memoryQueryService.answerFromHistory(rewrittenUserMessage, context.getSession().getId());
                break;
            
            // --- ✅ LOGIC MỚI ĐƯỢC TÍCH HỢP TẠI ĐÂY ---
            case DYNAMIC_QUERY:
            default:
                log.info("Intent requires dynamic tools, analyzing complexity for session {}.", context.getSession().getId());
                QueryComplexity complexity = complexityAnalyzer.analyze(rewrittenUserMessage);

                if (complexity == QueryComplexity.SIMPLE) {
                    chosenAgentName = "simpleRouterAgent";
                    log.info("Query is SIMPLE. Attempting with {} (GPT-3.5) for session {}.", chosenAgentName, context.getSession().getId());
                    try {
                        // ✅ SỬA LẠI CÁCH GỌI: TRUYỀN TOÀN BỘ CONTEXT
                        agentResponse = simpleRouterAgent.chat(context);
                    } catch (Exception e) {
                        log.warn("simpleRouterAgent failed for session {}. Reason: {}. Retrying with advancedRouterAgent (GPT-4).", context.getSession().getId(), e.getMessage());
                        chosenAgentName = "advancedRouterAgent_Fallback";
                        // ✅ SỬA LẠI CÁCH GỌI: TRUYỀN TOÀN BỘ CONTEXT
                        agentResponse = advancedRouterAgent.chat(context);
                    }
                } else {
                    chosenAgentName = "advancedRouterAgent";
                    log.info("Query is COMPLEX. Invoking {} (GPT-4) directly for session {}.", chosenAgentName, context.getSession().getId());
                    // ✅ SỬA LẠI CÁCH GỌI: TRUYỀN TOÀN BỘ CONTEXT
                    agentResponse = advancedRouterAgent.chat(context);
                }
                break;
        }
        
        String finalSanitizedAnswer = guardrailManager.checkOutput(agentResponse);

        return handleCachingAndPersistence(userMessage, rewrittenUserMessage, finalSanitizedAnswer, context, chosenAgentName);
    }
    
    // ... các phương thức private còn lại (handleCachingAndPersistence, determineContextForLookup, etc.) giữ nguyên ...

    private String handleCachingAndPersistence(String originalUserMessage, String rewrittenUserMessage, String finalAnswer, RagContext context, String chosenAgentName) {
        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "orchestrator");
            metadata.put("chosen_agent", chosenAgentName);
            ZonedDateTime validUntil = ZonedDateTime.now().plusDays(1);
            String contextForLookup = determineContextForLookup(rewrittenUserMessage, getLastBotMessage(context.getChatMemory().messages()));
            cacheService.saveToCache(rewrittenUserMessage, finalAnswer, contextForLookup, metadata, validUntil);
        }
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
    
    private String getLastBotMessage(List<ChatMessage> messages) {
         if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage) return messages.get(i).text();
        }
        return null;
    }

    private void saveConversationAndUpdateMemory(RagContext context, String userMessage, String assistantResponse) {
        try {
            chatMessageService.saveMessage(context.getSession(), "user", userMessage);
            chatMessageService.saveMessage(context.getSession(), "assistant", assistantResponse);
            context.getChatMemory().add(UserMessage.from(userMessage));
            context.getChatMemory().add(AiMessage.from(assistantResponse == null ? "" : assistantResponse));
        } catch (Exception e) {
            log.error("Error during persistence in Orchestrator for session {}: {}", context.getSession().getId(), e.getMessage(), e);
        }
    }
}