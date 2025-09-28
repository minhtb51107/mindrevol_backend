package com.example.demo.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.example.demo.service.chat.agent.FinancialAnalystAgent;
import com.example.demo.service.chat.agent.ToolAgent;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import com.example.demo.service.chat.tools.SerperWebSearchEngine;
import com.example.demo.service.chat.tools.StockTool;
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
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o")
                .temperature(0.7)
                .logRequests(true) // <<< DÒNG NÀY SẼ IN PROMPT RA LOG
                .logResponses(true)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    // <<< THAY ĐỔI: THÊM BEAN MỚI ĐỂ GIẢI QUYẾT LỖI >>>
    /**
     * Bean này cung cấp một mô hình ngôn ngữ nhanh, chi phí thấp (ví dụ: gpt-3.5-turbo)
     * dành riêng cho các tác vụ phụ trợ như viết lại câu hỏi trong QueryRewriteService.
     * Nó được đánh dấu bằng @Qualifier("on-demand-model") để Spring có thể inject chính xác.
     */
    @Bean
    @Qualifier("on-demand-model")
    public ChatLanguageModel onDemandChatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-3.5-turbo") // Model nhanh và hiệu quả cho các tác vụ phụ
                .temperature(0.3) // Nhiệt độ thấp để kết quả ổn định, ít sáng tạo
                .timeout(Duration.ofSeconds(20))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
    // <<< KẾT THÚC THAY ĐỔI >>>

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
                               WeatherTool weatherTool,
                               StockTool stockTool) {
        return AiServices.builder(ToolAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(serperWebSearchEngine, timeTool, weatherTool, stockTool)
                .build();
    }
    
    @Bean
    public QueryRouterService queryRouterService(ChatLanguageModel chatLanguageModel) {
        return AiServices.create(QueryRouterService.class, chatLanguageModel);
    }
    
    @Bean
    public FinancialAnalystAgent financialAnalystAgent(ChatLanguageModel chatLanguageModel) {
        return AiServices.create(FinancialAnalystAgent.class, chatLanguageModel);
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