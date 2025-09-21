package com.example.demo.service.chat.agent;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.service.chat.ChatMessageService; // <-- 1. Import service cần thiết
import com.example.demo.service.chat.guardrail.GuardrailManager;
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    private final Map<String, Agent> agents;
    private final ChatLanguageModel chatLanguageModel;
    private final Agent defaultAgent;
    private final GuardrailManager guardrailManager;
    private final MeterRegistry meterRegistry;
    private final ChatMessageService chatMessageService; // <-- 2. Inject ChatMessageService

    public OrchestratorService(List<Agent> agentList,
                               ChatLanguageModel chatLanguageModel,
                               GuardrailManager guardrailManager,
                               MeterRegistry meterRegistry,
                               ChatMessageService chatMessageService) { // <-- Thêm vào constructor
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.chatLanguageModel = chatLanguageModel;
        this.guardrailManager = guardrailManager;
        this.meterRegistry = meterRegistry;
        this.chatMessageService = chatMessageService; // <-- Khởi tạo
        this.defaultAgent = this.agents.get("RAGAgent");

        if (this.defaultAgent == null) {
            throw new IllegalStateException("A default agent with the name 'RAGAgent' must be available.");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(RagContext context) {
        // Giữ lại query gốc để lưu trữ
        final String originalUserInput = context.getInitialQuery();

        // BƯỚC 1: KIỂM TRA ĐẦU VÀO
        log.info("Checking user input against guardrails...");
        String safeUserInput = guardrailManager.checkInput(originalUserInput);
        if (!safeUserInput.equals(originalUserInput)) {
            log.warn("Input guardrail violation detected. Returning safe response.");
            return safeUserInput;
        }
        log.info("Input guardrails passed.");
        context.setInitialQuery(safeUserInput);

        // BƯỚC 2: CHỌN AGENT VÀ THỰC THI
        Agent chosenAgent = chooseAgent(safeUserInput);
        RagContext finalContext = chosenAgent.execute(context);
        String llmResponse = finalContext.getReply();

        // BƯỚC 3: KIỂM TRA ĐẦU RA
        log.info("Checking LLM output against guardrails...");
        String safeResponse = guardrailManager.checkOutput(llmResponse);
        log.info("Output guardrails passed.");

        // --- BƯỚC 4: LƯU LẠI CUỘC TRÒ CHUYỆN (FIX) ---
        persistConversation(finalContext, originalUserInput, safeResponse);
        // ---------------------------------------------

        return safeResponse;
    }

    private Agent chooseAgent(String safeUserInput) {
        String prompt = buildOrchestratorPrompt(safeUserInput);
        String chosenAgentName = chatLanguageModel.generate(prompt).trim();
        log.debug("Orchestrator LLM chose agent: '{}'", chosenAgentName);

        Agent chosenAgent = agents.get(chosenAgentName);
        boolean isFallback = false;

        if (chosenAgent == null) {
            log.warn("Could not find agent named '{}'. Falling back to default agent '{}'.",
                    chosenAgentName, defaultAgent.getName());
            chosenAgent = defaultAgent;
            isFallback = true;
        }

        meterRegistry.counter("agent.selected",
            "name", chosenAgent.getName(),
            "fallback", String.valueOf(isFallback)
        ).increment();

        return chosenAgent;
    }
    
    /**
     * Phương thức mới để lưu tin nhắn vào DB và cập nhật bộ nhớ.
     */
    private void persistConversation(RagContext context, String userQuery, String aiReply) {
        try {
            ChatSession session = context.getSession();
            if (session == null) {
                log.warn("Cannot persist conversation, session is null.");
                return;
            }

            // Cập nhật đối tượng ChatMemory trong bộ nhớ của ứng dụng
            ChatMemory chatMemory = context.getChatMemory();
            chatMemory.add(UserMessage.from(userQuery));
            chatMemory.add(AiMessage.from(aiReply));

            // Lưu vào cơ sở dữ liệu (PostgreSQL) để đảm bảo tính bền vững
            chatMessageService.saveMessage(session, "user", userQuery);
            chatMessageService.saveMessage(session, "assistant", aiReply);

            log.info("Successfully persisted conversation for session {}", session.getId());
        } catch (Exception e) {
            log.error("Failed to persist conversation for session {}: {}",
                    context.getSession() != null ? context.getSession().getId() : "null",
                    e.getMessage(), e);
        }
    }


    private String buildOrchestratorPrompt(String userInput) {
        // ... (phần này giữ nguyên không đổi)
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert AI routing system. Your task is to analyze the user's query and select the best specialized agent to handle it.\n");
        promptBuilder.append("Respond ONLY with the name of the chosen agent. Do not add any explanation or punctuation.\n\n");
        promptBuilder.append("Available agents:\n");

        for (Agent agent : agents.values()) {
            promptBuilder.append(String.format("- Name: %s, Description: %s\n", agent.getName(), agent.getDescription()));
        }

        promptBuilder.append("\nUser query: \"").append(userInput).append("\"\n");
        promptBuilder.append("Chosen agent name: ");

        return promptBuilder.toString();
    }
}