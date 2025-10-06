package com.example.demo.config;

import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.TokenUsageRepository;
import com.example.demo.service.chat.EmbeddingCacheService;
import com.example.demo.service.chat.agent.FinancialAnalystAgent;
import com.example.demo.service.chat.agent.RouterAgent;
import com.example.demo.service.chat.agent.ToolUsingAgent;
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
	
	/**
     * (MỚI) Định nghĩa một RouterAgent chi phí thấp, sử dụng GPT-3.5.
     * Agent này sẽ là lựa chọn đầu tiên cho các DYNAMIC_QUERY đơn giản.
     */
	@Bean
	@Qualifier("simpleRouterAgent")
	public ToolUsingAgent simpleRouterAgent(
	        @Qualifier("basicChatLanguageModel") ChatLanguageModel chatLanguageModel,
	        ChatMemoryProvider chatMemoryProvider,
	        AgentTools agentTools) {
	    return AiServices.builder(ToolUsingAgent.class)
	            .chatLanguageModel(chatLanguageModel)
	            .tools(agentTools)
	            .chatMemoryProvider(chatMemoryProvider)
	            // ✅ THÊM DÒNG NÀY ĐỂ HƯỚNG DẪN AGENT
	            .systemMessageProvider(sessionId -> "You are a helpful assistant. If you are unsure how to answer, use the 'searchWeb' tool.")
	            .build();
	}

    /**
     * (ĐỔI TÊN/CHỈNH SỬA) Agent mạnh mẽ sử dụng GPT-4.
     */
    @Bean
    @Qualifier("advancedRouterAgent")
    public ToolUsingAgent advancedRouterAgent( // ✅ SỬ DỤNG TYPE MỚI
            @Qualifier("advancedChatLanguageModel") ChatLanguageModel chatLanguageModel,
            ChatMemoryProvider chatMemoryProvider,
            AgentTools agentTools) {
        return AiServices.builder(ToolUsingAgent.class) // ✅ BUILD TỪ INTERFACE MỚI
                .chatLanguageModel(chatLanguageModel)
                .tools(agentTools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
    
	// ✅ THÊM BEAN NÀY VÀO
    // Tokenizer giúp đếm số token một cách chính xác trước khi gửi API
    @Bean
    public OpenAiTokenizer openAiTokenizer() {
        return new OpenAiTokenizer(OpenAiChatModelName.GPT_3_5_TURBO);
    }
	
	   // ✅ Cập nhật RouterAgent để sử dụng model nâng cao
//    @Bean
//    @Lazy
//    public RouterAgent routerAgent(@Qualifier("advancedChatLanguageModel") ChatLanguageModel chatLanguageModel, // <-- Sửa ở đây
//                                   AgentTools agentTools,
//                                   ChatMemoryProvider chatMemoryProvider) {
//        return AiServices.builder(RouterAgent.class)
//                .chatLanguageModel(chatLanguageModel) // Bây giờ nó sẽ nhận bean GPT-4
//                .tools(agentTools)
//                .chatMemoryProvider(chatMemoryProvider)
//                .build();
//    }

    // ✅ Sửa Bean chính: Chuyển sang GPT-3.5-TURBO làm mặc định
    @Bean
    @Primary
    @Lazy
    public ChatLanguageModel chatLanguageModel(TokenUsageRepository tokenUsageRepository,
                                               UserRepository userRepository) {
        OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO; // <-- THAY ĐỔI TẠI ĐÂY
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

    // ✅ Tạo một Bean mới dành riêng cho các tác vụ nâng cao
    /**
     * ✅ BEAN MỚI: Cung cấp model chi phí thấp (GPT-3.5) cho các tác vụ đơn giản.
     * Đây chính là bean đã bị thiếu và gây ra lỗi khởi động.
     */
    @Bean
    @Qualifier("basicChatLanguageModel")
    @Lazy
    public ChatLanguageModel basicChatLanguageModel(TokenUsageRepository tokenUsageRepository,
                                                    UserRepository userRepository) {
        OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_3_5_TURBO;
        String modelName = modelEnum.toString();

        ChatLanguageModel openAiModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelEnum)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(45))
                .logRequests(true)
                .logResponses(true)
                .build();
        // Giả sử bạn có class TrackedChatLanguageModel để theo dõi token
        return new TrackedChatLanguageModel(openAiModel, modelName, tokenUsageRepository, userRepository);
    }

    /**
     * BEAN NÂNG CAO: Cung cấp model mạnh mẽ (GPT-4) cho các tác vụ phức tạp.
     */
    @Bean
    @Qualifier("advancedChatLanguageModel")
    @Lazy
    public ChatLanguageModel advancedChatLanguageModel(TokenUsageRepository tokenUsageRepository,
                                                       UserRepository userRepository) {
        OpenAiChatModelName modelEnum = OpenAiChatModelName.GPT_4_TURBO_PREVIEW;
        String modelName = modelEnum.toString();

        ChatLanguageModel openAiModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelEnum)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(90))
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

	// ✅ SỬA LẠI BEAN `chatMemoryProvider` ĐỂ DÙNG GIẢI PHÁP THAY THẾ
		@Bean
		public ChatMemoryProvider chatMemoryProvider() {
	        // MessageWindowChatMemory giữ lại N tin nhắn cuối cùng.
	        // Đây là cách đơn giản và hiệu quả để ngăn context phình to vô hạn.
			return sessionId -> MessageWindowChatMemory.builder()
					.id(sessionId)
					.maxMessages(12) // Giữ lại 12 tin nhắn gần nhất (6 cặp hỏi-đáp)
					.chatMemoryStore(new InMemoryChatMemoryStore())
					.build();
		}
	
	// ✅ BƯỚC 1: THÊM BEAN MỚI NÀY VÀO
    // Di chuyển interface ra khỏi AgentTools và định nghĩa nó ở đây hoặc trong một file riêng
    public interface OpenAIWebSearchAssistant {
        String search(String query);
    }
    
    @Bean
    public OpenAIWebSearchAssistant openAIWebSearchAssistant(
            @Qualifier("chatLanguageModel") ChatLanguageModel chatLanguageModel) { // Dùng model mạnh nhất
        return AiServices.create(OpenAIWebSearchAssistant.class, chatLanguageModel);
    }
}
