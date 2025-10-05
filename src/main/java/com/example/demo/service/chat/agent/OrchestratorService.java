package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.QuestionAnswerCacheService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryRewriteService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class OrchestratorService {

    private final ChatMessageService chatMessageService;
    private final QuestionAnswerCacheService cacheService;
    private final FollowUpQueryDetectionService followUpQueryDetectionService;
    private final QueryRewriteService queryRewriteService;
    private final RouterAgent routerAgent; // ✅ Chỉ cần duy nhất RouterAgent để điều phối

    @Autowired
    public OrchestratorService(ChatMessageService chatMessageService,
                               QuestionAnswerCacheService cacheService,
                               FollowUpQueryDetectionService followUpQueryDetectionService,
                               QueryRewriteService queryRewriteService,
                               RouterAgent routerAgent) {
        this.chatMessageService = chatMessageService;
        this.cacheService = cacheService;
        this.followUpQueryDetectionService = followUpQueryDetectionService;
        this.queryRewriteService = queryRewriteService;
        this.routerAgent = routerAgent;
        log.info("Orchestrator initialized with a simplified, powerful RouterAgent architecture.");
    }

    public String orchestrate(String userMessage, RagContext context, boolean regenerate) {
        Long sessionId = context.getSession().getId();
        List<ChatMessage> chatHistory = context.getChatMemory().messages();
        String rewrittenUserMessage = userMessage;

        if (followUpQueryDetectionService.isFollowUp(userMessage)) {
            rewrittenUserMessage = queryRewriteService.rewrite(chatHistory, userMessage);
        }
        
        if (!regenerate) {
            Optional<String> cachedAnswer = cacheService.findCachedAnswer(rewrittenUserMessage, null);
            if (cachedAnswer.isPresent()) {
                log.info("Cache hit for session {}. Returning cached answer.", sessionId);
                String answerFromCache = cachedAnswer.get();
                saveConversationAndUpdateMemory(context, userMessage, answerFromCache);
                return answerFromCache;
            }
        }
        log.info("Cache miss for session {}. Invoking RouterAgent.", sessionId);
        
        // ✅ GIAO TOÀN BỘ QUYỀN QUYẾT ĐỊNH CHO ROUTERAGENT (GPT-4)
        String finalAnswer = routerAgent.chat(sessionId, rewrittenUserMessage);

        // Lưu trữ kết quả
        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            cacheService.saveToCache(rewrittenUserMessage, finalAnswer, null, new HashMap<>(), ZonedDateTime.now().plusDays(1));
        }
        saveConversationAndUpdateMemory(context, userMessage, finalAnswer);
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
            log.info("Orchestrator saved conversation and updated memory for session {}", context.getSession().getId());
        } catch (Exception e) {
            log.error("Error during persistence in Orchestrator for session {}: {}", context.getSession().getId(), e.getMessage(), e);
        }
    }
}