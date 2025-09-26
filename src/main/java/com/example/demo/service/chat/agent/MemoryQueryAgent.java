// src/main/java/com/example/demo/service/chat/agent/MemoryQueryAgent.java
package com.example.demo.service.chat.agent;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryQueryAgent implements Agent {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMessageService chatMessageService;

    @Override
    public String getName() {
        return "MemoryQueryAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này để trả lời các câu hỏi liên quan đến lịch sử cuộc trò chuyện.";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing FINAL MemoryQueryAgent for query: '{}'", context.getInitialQuery());

        // 1. Lấy lịch sử đã được lưu trong DB (tính đến trước lượt này)
        List<ChatMessage> historyFromDb = chatMessageService.getMessagesForSession(
                context.getSession().getId(),
                context.getUser()
        );

        // 2. Lấy câu hỏi hiện tại của người dùng từ context
        String currentUserQuery = context.getInitialQuery();

        // 3. Xây dựng prompt kết hợp cả hai nguồn
        String prompt = buildFinalPrompt(historyFromDb, currentUserQuery);

        // 4. Gọi LLM và trả về kết quả
        String response = chatLanguageModel.generate(prompt);
        context.setReply(response);
        return context;
    }

    /**
     * Phương thức cuối cùng: Xây dựng prompt từ lịch sử DB và câu hỏi hiện tại.
     */
    private String buildFinalPrompt(List<ChatMessage> dbHistory, String currentUserQuery) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là một trợ lý AI hữu ích có khả năng ghi nhớ và trả lời các câu hỏi về cuộc trò chuyện trước đây.\n");
        promptBuilder.append("Dưới đây là lịch sử trò chuyện đã diễn ra. Hãy dựa vào lịch sử này để trả lời câu hỏi cuối cùng của Người dùng.\n\n");
        promptBuilder.append("--- LỊCH SỬ TRÒ CHUYỆN ĐÃ LƯU ---\n");

        if (dbHistory.isEmpty()) {
            promptBuilder.append("(Chưa có lịch sử nào được lưu)\n");
        } else {
            for (ChatMessage message : dbHistory) {
                String role = "user".equalsIgnoreCase(message.getSender()) ? "Người dùng" : "Trợ lý AI";
                promptBuilder.append(String.format("%s: %s\n", role, message.getContent()));
            }
        }

        promptBuilder.append("--- KẾT THÚC LỊCH SỬ ---\n\n");
        
        // ✅ THAY ĐỔI QUAN TRỌNG:
        // Chúng ta chỉ đưa câu hỏi hiện tại vào phần cuối cùng của prompt,
        // tách biệt nó khỏi lịch sử đã lưu.
        promptBuilder.append("Dựa vào lịch sử trên, hãy trả lời câu hỏi sau của Người dùng:\n");
        promptBuilder.append("Người dùng: \"").append(currentUserQuery).append("\"\n\n");
        promptBuilder.append("Câu trả lời của bạn (Trợ lý AI):");

        return promptBuilder.toString();
    }
}