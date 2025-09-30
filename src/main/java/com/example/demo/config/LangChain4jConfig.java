package com.example.demo.config;

import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import com.example.demo.service.chat.agent.FinancialAnalystAgent;
import com.example.demo.service.chat.agent.ToolAgent;
import com.example.demo.service.chat.integration.RoutingTrackedChatLanguageModel;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel;
import com.example.demo.service.chat.integration.TrackedEmbeddingModel;
import com.example.demo.service.chat.orchestration.rules.FollowUpQueryDetectionService;
import com.example.demo.service.chat.orchestration.rules.QueryIntentClassificationService;
import com.example.demo.service.chat.orchestration.rules.QueryRouterService;
import com.example.demo.service.chat.orchestration.rules.QueryRewriteService;
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
import dev.langchain4j.model.openai.*; // Sửa 1: Import cả package
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

// Sửa 2: Xóa import cũ không cần thiết
// import static dev.langchain4j.model.openai.OpenAiModelName.*;

@Configuration
public class LangChain4jConfig {

	@Value("${langchain4j.openai.api-key}")
	private String openAiApiKey;

	// --- CẤU HÌNH CÁC MODEL CHÍNH ---

	@Bean
	@Primary
	public ChatLanguageModel chatLanguageModel(TokenUsageRepository tokenUsageRepository,
			UserRepository userRepository) {
		// Sửa 3: Sử dụng Enum và gọi .toString() khi cần
		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_4_TURBO_PREVIEW; // Dùng model mới nhất có thể
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum) // Truyền thẳng Enum vào đây
				.temperature(0.7).timeout(Duration.ofSeconds(45)).logRequests(true).logResponses(true).build();
		return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	@Qualifier("routingModel")
	public ChatLanguageModel routingModel(TokenUsageRepository tokenUsageRepository, UserRepository userRepository) {
		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO;
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum) // Truyền thẳng Enum
				.temperature(0.0).timeout(Duration.ofSeconds(20)).build();
		return new RoutingTrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	@Qualifier("classificationModel")
	public ChatLanguageModel classificationModel(TokenUsageRepository tokenUsageRepository,
			UserRepository userRepository) {
		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO;
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum) // Truyền thẳng Enum
				.temperature(0.3).timeout(Duration.ofSeconds(20)).build();
		return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	public EmbeddingModel embeddingModel(TokenUsageRepository tokenUsageRepository, UserRepository userRepository) {
		OpenAiEmbeddingModelName modelEnum = OpenAiEmbeddingModelName.TEXT_EMBEDDING_ADA_002;
		String modelName = modelEnum.toString();
		
		EmbeddingModel openAiEmbeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum) // Truyền thẳng Enum
				.timeout(Duration.ofSeconds(30)).build();
		return new TrackedEmbeddingModel(openAiEmbeddingModel, modelName, tokenUsageRepository, userRepository);
	}

	// --- CẤU HÌNH CÁC AI SERVICE (Không thay đổi) ---
	@Bean
	public QueryRouterService queryRouterService(@Qualifier("routingModel") ChatLanguageModel model) {
		return AiServices.create(QueryRouterService.class, model);
	}

	@Bean
	public QueryIntentClassificationService queryIntentClassificationService(@Qualifier("classificationModel") ChatLanguageModel model) {
		return AiServices.create(QueryIntentClassificationService.class, model);
	}

	@Bean
	public FollowUpQueryDetectionService followUpQueryDetectionService(@Qualifier("classificationModel") ChatLanguageModel model) {
		return AiServices.create(FollowUpQueryDetectionService.class, model);
	}

	@Bean
	public QueryRewriteService queryRewriteService(@Qualifier("classificationModel") ChatLanguageModel model) {
		return AiServices.create(QueryRewriteService.class, model);
	}

	@Bean
	public FinancialAnalystAgent financialAnalystAgent(ChatLanguageModel chatLanguageModel) {
		return AiServices.create(FinancialAnalystAgent.class, chatLanguageModel);
	}
	
	@Bean
	public ToolAgent toolAgent(ChatLanguageModel chatLanguageModel,
	                           ChatMemoryProvider chatMemoryProvider, // Thêm tham số này
	                           SerperWebSearchEngine serperWebSearchEngine,
	                           TimeTool timeTool,
	                           WeatherTool weatherTool,
	                           StockTool stockTool) {
	    return AiServices.builder(ToolAgent.class)
	            .chatLanguageModel(chatLanguageModel)
	            .chatMemoryProvider(chatMemoryProvider) // Cung cấp memory provider
	            .tools(serperWebSearchEngine, timeTool, weatherTool, stockTool)
	            .build();
	}

	// --- CÁC BEAN PHỤ TRỢ KHÁC ---

//	@Bean
//	public StreamingChatLanguageModel streamingChatLanguageModel(
//			@Value("${langchain4j.open-ai.chat-model.model-name}") String chatModelName) {
//		return OpenAiStreamingChatModel.builder().apiKey(openAiApiKey).modelName(chatModelName)
//				.temperature(0.7).timeout(Duration.ofSeconds(60)).build();
//	}

	@Bean
	public DocumentSplitter documentSplitter() {
		// Sửa 4: Sử dụng tokenizer của model GPT-4
		return DocumentSplitters.recursive(500, 50, new OpenAiTokenizer(OpenAiChatModelName.GPT_4));
	}

	@Bean
	public ChatMemoryProvider chatMemoryProvider() {
		return sessionId -> MessageWindowChatMemory.builder().id(sessionId).maxMessages(20)
				.chatMemoryStore(new InMemoryChatMemoryStore()).build();
	}
}