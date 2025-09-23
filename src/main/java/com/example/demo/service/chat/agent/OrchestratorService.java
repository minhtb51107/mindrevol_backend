package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.guardrail.GuardrailManager;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel; // ✅ 1. Import service mới
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.AiMessage; // ✅ 2. Import AiMessage
import dev.langchain4j.data.message.UserMessage; // ✅ 2. Import UserMessage
import dev.langchain4j.model.output.Response; // ✅ 2. Import Response
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrchestratorService {

    private final Map<String, Agent> agents;
    // ✅ 3. Thay thế ChatLanguageModel bằng TrackedChatLanguageModel
    private final TrackedChatLanguageModel trackedChatLanguageModel;
    private final Agent defaultAgent;
    private final GuardrailManager guardrailManager;
    private final MeterRegistry meterRegistry;

    // ✅ 4. Cập nhật Constructor
    public OrchestratorService(List<Agent> agentList,
                               TrackedChatLanguageModel trackedChatLanguageModel, // Thay đổi ở đây
                               GuardrailManager guardrailManager,
                               MeterRegistry meterRegistry) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.trackedChatLanguageModel = trackedChatLanguageModel; // Thay đổi ở đây
        this.guardrailManager = guardrailManager;
        this.meterRegistry = meterRegistry;
        this.defaultAgent = this.agents.get("RAGAgent");
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(RagContext context) {
        try {
            String safeUserInput = guardrailManager.checkInput(context.getInitialQuery());
            if (!safeUserInput.equals(context.getInitialQuery())) {
                if (context.getSseEmitter() != null) {
                    try { context.getSseEmitter().complete(); } catch (Exception e) {}
                }
                return safeUserInput;
            }
            context.setInitialQuery(safeUserInput);

            // ✅ 5. Truyền toàn bộ context vào chooseAgent
            Agent chosenAgent = chooseAgent(context);
            RagContext finalContext = chosenAgent.execute(context);
            
            return finalContext.getReply();

        } catch (Exception e) {
            log.error("Error during orchestration for session {}: {}", context.getSession().getId(), e.getMessage(), e);
            if (context.getSseEmitter() != null) {
                 try {
                    context.getSseEmitter().send(SseEmitter.event().name("error").data("Lỗi hệ thống."));
                } catch (Exception ex) {
                    log.warn("Could not send error to SSE client.", ex);
                } finally {
                     try { context.getSseEmitter().complete(); } catch (Exception ex) {}
                }
            }
            return "Rất tiếc, đã có lỗi hệ thống xảy ra.";
        }
    }

    @Retryable(
        value = { RuntimeException.class, UncheckedIOException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    // ✅ 6. Sửa đổi phương thức chooseAgent để nhận RagContext
    private Agent chooseAgent(RagContext context) {
        String prompt = buildOrchestratorPrompt(context.getInitialQuery());

        // ✅ 7. Gọi LLM thông qua service đã tích hợp tracking
        Response<AiMessage> response = trackedChatLanguageModel.generate(
                Collections.singletonList(new UserMessage(prompt)), // Bọc prompt trong UserMessage
                context.getUser().getId(),
                context.getSession().getId()
        );
        String chosenAgentName = response.content().text().trim();
        
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
    
    private String buildOrchestratorPrompt(String userInput) {
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