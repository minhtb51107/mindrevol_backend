package com.example.demo.service.chat.agent;

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

    public OrchestratorService(List<Agent> agentList, ChatLanguageModel chatLanguageModel) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::getName, Function.identity()));
        this.chatLanguageModel = chatLanguageModel;
        this.defaultAgent = this.agents.get("RAGAgent"); // Set RAGAgent as the default fallback

        if (this.defaultAgent == null) {
            throw new IllegalStateException("A default agent with the name 'RAGAgent' must be available.");
        }
        log.info("Orchestrator initialized with {} agents: {}", agents.size(), agents.keySet());
    }

    public String orchestrate(RagContext context) {
        // 1. Build the prompt for the orchestrator LLM
        String userInput = context.getInitialQuery();
        String prompt = buildOrchestratorPrompt(userInput);

        // 2. Call the LLM to choose the appropriate agent
        String chosenAgentName = chatLanguageModel.generate(prompt).trim();
        log.debug("Orchestrator LLM chose agent: '{}'", chosenAgentName);

        // 3. Get the chosen agent and execute it
        Agent chosenAgent = agents.get(chosenAgentName);

        RagContext finalContext;
        if (chosenAgent != null) {
            finalContext = chosenAgent.execute(context);
        } else {
            // Fallback: If the LLM hallucinates an agent name or fails, use the default agent
            log.warn("Could not find agent named '{}'. Falling back to default agent '{}'.",
                    chosenAgentName, defaultAgent.getName());
            finalContext = defaultAgent.execute(context);
        }
        
        return finalContext.getReply();
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