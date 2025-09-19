package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.preference.UserPreferenceService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GenerationStep implements RagStep {

    private final ChatLanguageModel chatLanguageModel;
    private final UserPreferenceService userPreferenceService;

    @Override
    public RagContext execute(RagContext context) {
        // 1. Lấy sở thích người dùng
        Map<String, Object> userPrefs = userPreferenceService.getUserPreferencesForPrompt(context.getUser().getId());
        context.setUserPreferences(userPrefs);

        // 2. Lấy bối cảnh RAG (sẽ là rỗng nếu là CHITCHAT)
        String ragContext = context.getRagContextString() != null ? context.getRagContextString() : "";

        // 3. Build prompt
        List<ChatMessage> finalLcMessages = buildFinalLc4jMessages(
                context.getChatMemory().messages(),
                ragContext,
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
    private List<ChatMessage> buildFinalLc4jMessages(
            List<ChatMessage> history,
            String ragContext,
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

        sb.append("\n--- BỐI CẢNH NGẮN HẠN (TỪ RAG) ---\n");
        sb.append(ragContext.isEmpty() ? "Không có" : ragContext).append("\n");
        sb.append("\n--- HẾT BỐI CẢNH ---\n\nHãy trả lời câu hỏi hiện tại.");

        messages.add(SystemMessage.from(sb.toString()));
        messages.addAll(history);
        messages.add(UserMessage.from(currentQuery));

        return messages;
    }
}