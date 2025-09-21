package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.guardrail.GuardrailManager; // <-- MODIFIED: Import GuardrailManager
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private final GuardrailManager guardrailManager; // <-- MODIFIED: Add GuardrailManager field

    // <-- MODIFIED: Add GuardrailManager to the constructor
    public OrchestratorService(List<Agent> agentList, ChatLanguageModel chatLanguageModel, GuardrailManager guardrailManager) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.chatLanguageModel = chatLanguageModel;
        this.guardrailManager = guardrailManager; // <-- MODIFIED: Initialize the field
        this.defaultAgent = this.agents.get("RAGAgent"); // Set RAGAgent as the default fallback

        if (this.defaultAgent == null) {
            throw new IllegalStateException("A default agent with the name 'RAGAgent' must be available.");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(RagContext context) {
        final String originalUserInput = context.getInitialQuery();

        // ==========================================================
        // BƯỚC 1: KIỂM TRA ĐẦU VÀO (INPUT GUARDRAILS)
        // ==========================================================
        log.info("Checking user input against guardrails...");
        String safeUserInput = guardrailManager.checkInput(originalUserInput);

        // Nếu đầu vào không an toàn, trả về thông báo và dừng lại
        if (!safeUserInput.equals(originalUserInput)) {
            log.warn("Input guardrail violation detected. Original input: [{}]. Returning safe response.", originalUserInput);
            // Không cần cập nhật context vì chúng ta trả về ngay lập tức
            return safeUserInput;
        }
        log.info("Input guardrails passed.");
        
        // Cập nhật context với đầu vào đã được làm sạch để các bước sau sử dụng
        context.setInitialQuery(safeUserInput);


        // ==========================================================
        // BƯỚC 2: LOGIC CHỌN AGENT (NHƯ CŨ)
        // ==========================================================
        
        // 2.1. Build the prompt for the orchestrator LLM, now using the safe input
        String prompt = buildOrchestratorPrompt(safeUserInput);

        // 2.2. Call the LLM to choose the appropriate agent
        String chosenAgentName = chatLanguageModel.generate(prompt).trim();
        log.debug("Orchestrator LLM chose agent: '{}'", chosenAgentName);

        // 2.3. Get the chosen agent and execute it
        Agent chosenAgent = agents.get(chosenAgentName);

        RagContext finalContext;
        if (chosenAgent != null) {
            // Agent sẽ thực thi với context chứa `safeUserInput`
            finalContext = chosenAgent.execute(context);
        } else {
            log.warn("Could not find agent named '{}'. Falling back to default agent '{}'.",
                    chosenAgentName, defaultAgent.getName());
            finalContext = defaultAgent.execute(context);
        }
        
        String llmResponse = finalContext.getReply();

        // ==========================================================
        // BƯỚC 3: KIỂM TRA ĐẦU RA (OUTPUT GUARDRAILS)
        // ==========================================================
        log.info("Checking LLM output against guardrails...");
        String safeResponse = guardrailManager.checkOutput(llmResponse);
        log.info("Output guardrails passed.");

        return safeResponse;
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