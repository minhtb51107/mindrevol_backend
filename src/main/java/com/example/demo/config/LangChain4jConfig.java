// src/main/java/com/example/demo/config/LangChain4jConfig.java

package com.example.demo.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary; // ✅ 1. Thêm import này

import com.example.demo.service.chat.agent.ToolAgent;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import com.example.demo.service.chat.tools.SerperWebSearchEngine;
import com.example.demo.service.chat.tools.TimeTool;
import com.example.demo.service.chat.tools.WeatherTool;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import dev.langchain4j.web.search.WebSearchEngine;

@Configuration
public class LangChain4jConfig {
	
    @Value("${langchain4j.openai.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String chatModelName;

    @Bean
    @Primary // ✅ 2. Đánh dấu đây là bean mặc định cho ChatLanguageModel
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o")
                .temperature(0.7)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-3-small")
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return session_id -> MessageWindowChatMemory.builder()
                .id(session_id)
                .maxMessages(20)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
    }
    
    @Bean
    public DocumentSplitter documentSplitter() {
    	return DocumentSplitters.recursive(
    	        500,
    	        50,
    	        new OpenAiTokenizer("gpt-4o")
    	);
    }
    
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(chatModelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
    
    @Bean
    public ToolAgent toolAgent(ChatLanguageModel chatLanguageModel,
                               ChatMemoryProvider chatMemoryProvider,
                               SerperWebSearchEngine serperWebSearchEngine,
                               TimeTool timeTool,
                               WeatherTool weatherTool) {
        return AiServices.builder(ToolAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(serperWebSearchEngine, timeTool, weatherTool)
                .build();
    }
    
    @Bean
    public QueryRouterService queryRouterService(ChatLanguageModel chatLanguageModel) {
        return AiServices.create(QueryRouterService.class, chatLanguageModel);
    }
    
    @Bean
    @Qualifier("routingChatModel")
    public ChatLanguageModel routingChatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-3.5-turbo")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(20))
                .maxRetries(2)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}