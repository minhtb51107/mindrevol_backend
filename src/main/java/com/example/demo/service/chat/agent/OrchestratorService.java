package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.guardrail.GuardrailManager;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService; // ✅ 1. Import QueryRouterService
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
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
    private final TrackedChatLanguageModel trackedChatLanguageModel;
    private final Agent defaultAgent;
    private final GuardrailManager guardrailManager;
    private final MeterRegistry meterRegistry;
    private final QueryRouterService queryRouterService; // ✅ 2. Thêm QueryRouterService làm dependency

    // ✅ 3. Cập nhật Constructor để nhận QueryRouterService
    public OrchestratorService(List<Agent> agentList,
                               TrackedChatLanguageModel trackedChatLanguageModel,
                               GuardrailManager guardrailManager,
                               MeterRegistry meterRegistry,
                               QueryRouterService queryRouterService) { // Thêm vào đây
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.trackedChatLanguageModel = trackedChatLanguageModel;
        this.guardrailManager = guardrailManager;
        this.meterRegistry = meterRegistry;
        this.queryRouterService = queryRouterService; // Khởi tạo service
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
    // ✅ 4. Cập nhật toàn bộ phương thức chooseAgent với logic mới
    private Agent chooseAgent(RagContext context) {
        // --- LOGIC MỚI BẮT ĐẦU TỪ ĐÂY ---

        // Bước 1: Phân loại câu hỏi trước
        QueryRouterService.QueryType queryType = queryRouterService.getQueryType(context.getInitialQuery());

        // Bước 2: Nếu là câu hỏi phức tạp, ưu tiên định tuyến thẳng đến RAGAgent
        if (queryType == QueryRouterService.QueryType.COMPLEX) {
            log.debug("Query classified as COMPLEX, routing directly to RAGAgent.");
            Agent chosenAgent = agents.get("RAGAgent");
            // Đảm bảo agent tồn tại để tránh NullPointerException
            if (chosenAgent != null) {
                meterRegistry.counter("agent.selected", "name", chosenAgent.getName(), "fallback", "false", "route_type", "rule_based").increment();
                return chosenAgent;
            }
            log.warn("RAGAgent not found, falling back to default agent selection.");
        }

        // Bước 3: Nếu là câu hỏi đơn giản (SIMPLE), sử dụng logic LLM-based routing như cũ
        log.debug("Query classified as SIMPLE, using LLM to choose agent.");
        String prompt = buildOrchestratorPrompt(context.getInitialQuery());

        Response<AiMessage> response = trackedChatLanguageModel.generate(
                Collections.singletonList(new UserMessage(prompt)),
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
            "fallback", String.valueOf(isFallback),
            "route_type", "llm_based" // Thêm tag để phân biệt
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