package com.example.demo.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.data.document.DocumentSplitter;

import java.time.Duration;

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
}