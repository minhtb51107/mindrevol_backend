package com.example.demo.config;

import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import com.example.demo.service.chat.EmbeddingCacheService;
import com.example.demo.service.chat.agent.FinancialAnalystAgent;
import com.example.demo.service.chat.agent.RouterAgent;
//import com.example.demo.service.chat.agent.ToolAgent;
import com.example.demo.service.chat.agent.tools.AgentTools;
import com.example.demo.service.chat.integration.CachedEmbeddingModel;
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
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.*;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy; // ✅ Thêm import @Lazy

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

	@Value("${langchain4j.openai.api-key}")
	private String openAiApiKey;

	// --- CẤU HÌNH CÁC MODEL CHÍNH ---

	@Bean
	public RouterAgent routerAgent(@Qualifier("chatLanguageModel") ChatLanguageModel chatLanguageModel,
								   AgentTools agentTools,
								   ChatMemoryProvider chatMemoryProvider) {
		return AiServices.builder(RouterAgent.class)
				.chatLanguageModel(chatLanguageModel)
				.tools(agentTools)
				.chatMemoryProvider(chatMemoryProvider)
				.build();
	}

	@Bean
	@Primary
	@Lazy // ✅ Thêm @Lazy
	public ChatLanguageModel chatLanguageModel(TokenUsageRepository tokenUsageRepository,
			UserRepository userRepository) {

		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_4_TURBO_PREVIEW;
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum)
				.temperature(0.7)
				.timeout(Duration.ofSeconds(45))
				.logRequests(true)
				.logResponses(true)
				.build();

		return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	@Qualifier("classificationModel")
	@Lazy // ✅ Thêm @Lazy
	public ChatLanguageModel classificationModel(TokenUsageRepository tokenUsageRepository,
			UserRepository userRepository) {
		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO;
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum)
				.temperature(0.3)
				.timeout(Duration.ofSeconds(20))
				.build();

		return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	@Lazy // ✅ Thêm @Lazy
	public EmbeddingModel embeddingModel(TokenUsageRepository tokenUsageRepository, 
										 UserRepository userRepository,
										 EmbeddingCacheService embeddingCacheService) {

		OpenAiEmbeddingModelName modelEnum = OpenAiEmbeddingModelName.TEXT_EMBEDDING_ADA_002;
		String modelName = modelEnum.toString();

		EmbeddingModel openAiEmbeddingModel = OpenAiEmbeddingModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum)
				.timeout(Duration.ofSeconds(30))
				.build();

		EmbeddingModel trackedModel = new TrackedEmbeddingModel(openAiEmbeddingModel, modelName, tokenUsageRepository, userRepository);

		return new CachedEmbeddingModel(trackedModel, embeddingCacheService);
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
	@Qualifier("titleGenerationModel")
	@Lazy // ✅ Thêm @Lazy
	public ChatLanguageModel titleGenerationModel(TokenUsageRepository tokenUsageRepository,
												  UserRepository userRepository) {
		OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO;
		String modelName = modelEnum.toString();

		ChatLanguageModel openAiModel = OpenAiChatModel.builder()
				.apiKey(openAiApiKey)
				.modelName(modelEnum)
				.temperature(0.5)
				.timeout(Duration.ofSeconds(15))
				.build();

		return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
	}

	@Bean
	@Lazy // ✅ Thêm @Lazy
	public ModerationModel moderationModel(@Value("${langchain.chat-model.openai.api-key}") String apiKey) {
		return OpenAiModerationModel.builder()
				.apiKey(apiKey)
				.build();
	}

	@Bean
	public DocumentSplitter documentSplitter() {
		return DocumentSplitters.recursive(500, 50, new OpenAiTokenizer(OpenAiChatModelName.GPT_4));
	}

	@Bean
	public ChatMemoryProvider chatMemoryProvider() {
		return sessionId -> MessageWindowChatMemory.builder()
				.id(sessionId)
				.maxMessages(20)
				.chatMemoryStore(new InMemoryChatMemoryStore())
				.build();
	}
}
