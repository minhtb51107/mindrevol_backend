package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.config.monitoring.LogExecutionTime;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineStep;
import com.example.demo.service.chat.preference.UserPreferenceService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GenerationStep implements PipelineStep  {

    private final ChatLanguageModel chatLanguageModel;
    private final UserPreferenceService userPreferenceService;
    
    // ✅ THAY ĐỔI 3: Thêm phương thức getStepName()
    @Override
    public String getStepName() {
        return "generation"; // Tên này phải khớp với trong application.yml
    }


    @Override
    @LogExecutionTime
    public RagContext execute(RagContext context) {
        // 1. Lấy sở thích người dùng
        Map<String, Object> userPrefs = userPreferenceService.getUserPreferencesForPrompt(context.getUser().getId());
        context.setUserPreferences(userPrefs);

        // 2. Lấy bối cảnh RAG và file context từ các matches đã được truy xuất
        String ragContext = context.getRagContextString() != null ? context.getRagContextString() : "";

        // ✅ LOGIC MỚI: Lấy ngữ cảnh file từ các matches đã được truy xuất
        String fileContext = "";
        if (context.getRetrievedMatches() != null) {
            StringBuilder fileContextBuilder = new StringBuilder();
            for (EmbeddingMatch<TextSegment> match : context.getRetrievedMatches()) {
                if ("temp_file".equals(match.embedded().metadata().get("docType"))) {
                    fileContextBuilder.append(match.embedded().text()).append("\n");
                }
            }
            fileContext = fileContextBuilder.toString().trim();
        }

        // 3. Build prompt
        List<ChatMessage> finalLcMessages = buildFinalLc4jMessages(
                context.getChatMemory().messages(),
                ragContext,
                fileContext,
                userPrefs,
                context.getInitialQuery()
        );
        context.setFinalLcMessages(finalLcMessages);

        // 4. Gọi AI
        Response<AiMessage> response = chatLanguageModel.generate(finalLcMessages);
        String reply = response.content().text();

        // 5. Set kết quả
        context.setReply(reply);

        return context;
    }

    // Di chuyển logic build prompt từ ChatAIService
    // ✅ SỬA LẠI HÀM NÀY
    private List<ChatMessage> buildFinalLc4jMessages(
            List<ChatMessage> history,
            String ragContext,
            String fileContext,
            Map<String, Object> userPrefsMap,
            String currentQuery) {

        List<ChatMessage> messages = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý AI hữu ích.\n");

        if (userPrefsMap != null && !userPrefsMap.isEmpty()) {
            sb.append("\n--- SỞ THÍCH CỦA NGƯỜI DÙNG ---\n");
            userPrefsMap.forEach((key, value) -> {
                sb.append(String.format("%s: %s\n", key, value != null ? value.toString() : "N/A"));
            });
        }

        // ✅ LOGIC MỚI: ƯU TIÊN FILE CONTEXT
        // LOGIC MỚI: ƯU TIÊN FILE CONTEXT
        if (fileContext != null && !fileContext.isBlank()) {
            sb.append("\n--- NGỮ CẢNH TỪ FILE ĐÍNH KÈM (ƯU TIÊN CAO) ---\n");
            sb.append(fileContext).append("\n");
            sb.append("--- HẾT NGỮ CẢNH FILE ---\n\n");
        }

        // ✅ SỬA LẠI PROMPT
        sb.append("\n--- BỐI CẢNH TỪ BỘ NHỚ RAG (NẾU CÓ) ---\n");
        sb.append(ragContext.isEmpty() ? "Không có" : ragContext).append("\n");
        sb.append("\n--- HẾT BỐI CẢNH ---\n\nHãy trả lời câu hỏi hiện tại dựa trên các ngữ cảnh trên (ưu tiên ngữ cảnh file).");

        messages.add(SystemMessage.from(sb.toString()));
        messages.addAll(history);
        messages.add(UserMessage.from(currentQuery));

        return messages;
    }
}