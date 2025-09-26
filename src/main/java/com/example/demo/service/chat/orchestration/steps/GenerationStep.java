package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.guardrail.GuardrailManager;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.orchestration.pipeline.result.GenerationStepResult;
import com.example.demo.service.chat.preference.UserPreferenceService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GenerationStep implements PipelineStep {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final UserPreferenceService userPreferenceService;
    // --- DEPENDENCIES MỚI ---
    private final GuardrailManager guardrailManager;
    private final ChatMessageService chatMessageService;


    public GenerationStep(ChatLanguageModel chatLanguageModel,
                          StreamingChatLanguageModel streamingChatLanguageModel,
                          UserPreferenceService userPreferenceService,
                          @Lazy GuardrailManager guardrailManager, // @Lazy để tránh vòng lặp dependency
                          ChatMessageService chatMessageService) {
        this.chatLanguageModel = chatLanguageModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.userPreferenceService = userPreferenceService;
        this.guardrailManager = guardrailManager;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public String getStepName() {
        return "generation";
    }

    @Override
    @LogExecutionTime
    public GenerationStepResult execute(RagContext context) {
        List<ChatMessage> finalLcMessages = buildFinalLc4jMessages(context);
        
        // Logic cho streaming hoặc blocking vẫn giữ nguyên, 
        // nhưng kết quả cuối cùng sẽ được đóng gói vào GenerationStepResult.
        
        // Ví dụ với blocking generation:
        Response<AiMessage> response = chatLanguageModel.generate(finalLcMessages);
        String finalReply = response.content().text();
        String safeResponse = guardrailManager.checkOutput(finalReply);
        persistConversation(context, context.getInitialQuery(), safeResponse);

        return GenerationStepResult.builder()
                .finalLcMessages(finalLcMessages)
                .reply(safeResponse)
                .build();
    }

    private void executeBlockingGeneration(RagContext context, List<ChatMessage> messages) {
        log.debug("Executing GenerationStep in BLOCKING mode for session {}", context.getSession().getId());
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        String finalReply = response.content().text();
        context.setReply(finalReply);
        
        // Thực hiện kiểm tra và lưu trữ cho chế độ đồng bộ
        String safeResponse = guardrailManager.checkOutput(finalReply);
        persistConversation(context, context.getInitialQuery(), safeResponse);
    }

    private void executeStreamingGeneration(RagContext context, List<ChatMessage> messages) {
        log.debug("Executing GenerationStep in STREAMING mode for session {}", context.getSession().getId());
        StringBuilder fullResponse = new StringBuilder();
        SseEmitter emitter = context.getSseEmitter();

        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                try {
                    emitter.send(SseEmitter.event().name("message").data(token));
                    fullResponse.append(token);
                } catch (IOException e) {
                    log.warn("Failed to send token to client for session {}. Client might have disconnected.", context.getSession().getId());
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                String finalReply = fullResponse.toString();
                log.info("Streaming completed for session {}. Final reply length: {}", context.getSession().getId(), finalReply.length());
                context.setReply(finalReply);
                
                try {
                    // --- LOGIC XỬ LÝ CUỐI CÙNG NẰM Ở ĐÂY ---
                    String safeResponse = guardrailManager.checkOutput(finalReply);
                    persistConversation(context, context.getInitialQuery(), safeResponse);
                } catch (Exception e) {
                    log.error("Error in onComplete handler for session {}: {}", context.getSession().getId(), e.getMessage(), e);
                } finally {
                    emitter.complete(); // Đóng emitter sau khi mọi thứ hoàn tất
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("Error during streaming generation for session {}", context.getSession().getId(), error);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Lỗi từ phía AI model."));
                } catch (IOException e) {
                    log.warn("Failed to send error event to client", e);
                } finally {
                    emitter.complete(); // Luôn đóng emitter khi có lỗi
                }
            }
        };

        streamingChatLanguageModel.generate(messages, handler);
    }
    
    private void persistConversation(RagContext context, String userQuery, String aiReply) {
    	ChatSession session = context.getSession();
        try {
            
            ChatMemory chatMemory = context.getChatMemory();
            
            chatMemory.add(UserMessage.from(userQuery));
            chatMemory.add(AiMessage.from(aiReply));
            
            chatMessageService.saveMessage(session, "user", userQuery);
            chatMessageService.saveMessage(session, "assistant", aiReply);

            log.info("Successfully persisted conversation for session {}", session.getId());
        } catch (Exception e) {
            log.error("Failed to persist conversation for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }
    
    private List<ChatMessage> buildFinalLc4jMessages(RagContext context) {
        Map<String, Object> userPrefs = userPreferenceService.getUserPreferencesForPrompt(context.getUser().getId());
        String ragContextStr = context.getRagContextString() != null ? context.getRagContextString() : "";
        String fileContext = extractFileContext(context);

        List<ChatMessage> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là một trợ lý AI hữu ích.\n");
        sb.append("--- HƯỚNG DẪN QUAN TRỌNG ---\n");
        sb.append("1. CHỈ trả lời câu hỏi dựa trên thông tin có trong \"NGỮ CẢNH TỪ FILE ĐÍNH KÈM\" hoặc \"BỐI CẢNH TỪ BỘ NHỚ RAG\".\n");
        sb.append("2. Nếu thông tin không có trong ngữ cảnh được cung cấp, hãy trả lời chính xác là: \"Tôi không tìm thấy thông tin về vấn đề này\".\n");
        sb.append("3. KHÔNG được tự ý suy diễn hoặc sử dụng kiến thức bên ngoài để trả lời.\n");
        sb.append("--- KẾT THÚC HƯỚNG DẪN ---\n\n");


        if (userPrefs != null && !userPrefs.isEmpty()) {
            sb.append("\n--- SỞ THÍCH CỦA NGƯỜI DÙNG ---\n");
            userPrefs.forEach((key, value) -> {
                sb.append(String.format("%s: %s\n", key, value != null ? value.toString() : "N/A"));
            });
        }

        if (fileContext != null && !fileContext.isBlank()) {
            sb.append("\n--- NGỮ CẢNH TỪ FILE ĐÍNH KÈM (ƯU TIÊN CAO) ---\n");
            sb.append(fileContext).append("\n");
            sb.append("--- HẾT NGỮ CẢNH FILE ---\n\n");
        }

        sb.append("\n--- BỐI CẢNH TỪ BỘ NHỚ RAG (NẾU CÓ) ---\n");
        sb.append(ragContextStr.isEmpty() ? "Không có" : ragContextStr).append("\n");
        sb.append("\n--- HẾT BỐI CẢNH ---\n\nHãy trả lời câu hỏi hiện tại dựa trên các ngữ cảnh trên (ưu tiên ngữ cảnh file).");

        messages.add(SystemMessage.from(sb.toString()));
        messages.addAll(context.getChatMemory().messages());
        messages.add(UserMessage.from(context.getInitialQuery()));

        return messages;
    }

    private String extractFileContext(RagContext context) {
        if (context.getRetrievedMatches() == null) return "";
        StringBuilder fileContextBuilder = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : context.getRetrievedMatches()) {
            if ("temp_file".equals(match.embedded().metadata().get("docType"))) {
                fileContextBuilder.append(match.embedded().text()).append("\n");
            }
        }
        return fileContextBuilder.toString().trim();
    }
}