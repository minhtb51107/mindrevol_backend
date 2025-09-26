package com.example.demo.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
	
	 // ✅ Tiêm key từ application.properties
    @Value("${langchain4j.openai.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String chatModelName;

    @Bean
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
    
//    @Bean
//    public ContentRetriever baseContentRetriever(
//            EmbeddingStore<TextSegment> embeddingStore,
//            EmbeddingModel embeddingModel) {
//        
//        return EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(embeddingStore)
//                .embeddingModel(embeddingModel)
//                .maxResults(20) // Lấy 20 kết quả thô, như bạn dự định
//                .build();
//    }
    

    // ✅ THÊM BEAN NÀY VÀO
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        // InMemoryChatMemoryStore sẽ lưu trữ lịch sử chat của TẤT CẢ người dùng trong bộ nhớ.
        // Điều này phù hợp cho việc thử nghiệm và các ứng dụng nhỏ.
        // Khi triển khai thực tế, bạn có thể cân nhắc dùng RedisChatMemoryStore.
        return session_id -> MessageWindowChatMemory.builder()
                .id(session_id)
                .maxMessages(20) // Lưu lại 20 tin nhắn gần nhất cho mỗi session
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
    }
    
    @Bean
    public DocumentSplitter documentSplitter() {
        // Đây là một bộ chia văn bản (splitter) thông minh.
        // Nó cố gắng chia nhỏ tài liệu theo các đoạn văn, dòng, v.v.
        // và đảm bảo mỗi đoạn (chunk) không vượt quá 500 token, 
        // với 50 token gối lên nhau (overlap) để giữ ngữ cảnh.
    	return DocumentSplitters.recursive(
    	        500,
    	        50,
    	        new OpenAiTokenizer("gpt-4o") // ✅ ĐÃ SỬA
    	);
    }
    
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(chatModelName)
                .temperature(0.7) // Đảm bảo đồng bộ nhiệt độ
                .timeout(Duration.ofSeconds(60)) // Đặt timeout
                .build();
    }
    
    // ✅ BƯỚC 2: SỬA LẠI PHƯƠNG THỨC toolAgent ĐỂ NHẬN TRỰC TIẾP SerperWebSearchEngine
    @Bean
    public ToolAgent toolAgent(ChatLanguageModel chatLanguageModel,
                               ChatMemoryProvider chatMemoryProvider,
                               SerperWebSearchEngine serperWebSearchEngine, // Thay WebSearchEngine bằng SerperWebSearchEngine
                               TimeTool timeTool,
                               WeatherTool weatherTool) {
        return AiServices.builder(ToolAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                // LangChain4j đủ thông minh để biết SerperWebSearchEngine là một WebSearchEngine
                .tools(serperWebSearchEngine, timeTool, weatherTool)
                .build();
    }
    
    // ✅ Thêm Bean này để tạo QueryRouterService
    @Bean
    public QueryRouterService queryRouterService(ChatLanguageModel chatLanguageModel) {
        return AiServices.create(QueryRouterService.class, chatLanguageModel);
    }
    
    @Bean
    @Qualifier("routingChatModel") // Đặt tên định danh cho Bean này
    public ChatLanguageModel routingChatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-3.5-turbo") // <-- Sử dụng model rẻ hơn
                .temperature(0.0) // Việc định tuyến cần sự chính xác, không cần sáng tạo
                .timeout(Duration.ofSeconds(10))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}