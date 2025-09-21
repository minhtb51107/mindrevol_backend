package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MemoryQueryAgent implements Agent {

    private final ChatLanguageModel chatLanguageModel;

    public MemoryQueryAgent(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

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
        log.debug("Executing MemoryQueryAgent for query: '{}'", context.getInitialQuery());

        // --- LOGIC ĐÃ ĐƯỢC NÂNG CẤP ---
        // 1. Xây dựng một câu lệnh (prompt) rõ ràng và có cấu trúc
        String prompt = buildMemoryQueryPrompt(context.getChatMemory(), context.getInitialQuery());

        // 2. Gọi LLM với câu lệnh đã được cải tiến
        String response = chatLanguageModel.generate(prompt);
        // ---------------------------------

        context.setReply(response);
        return context;
    }

    /**
     * Xây dựng một câu lệnh chi tiết để hướng dẫn LLM hiểu đúng vai trò
     * và trả lời chính xác các câu hỏi về lịch sử trò chuyện.
     * @param chatMemory Lịch sử trò chuyện
     * @param userQuery Câu hỏi hiện tại của người dùng
     * @return Một chuỗi prompt hoàn chỉnh
     */
    private String buildMemoryQueryPrompt(ChatMemory chatMemory, String userQuery) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Bạn là một trợ lý AI hữu ích có khả năng ghi nhớ và trả lời các câu hỏi về cuộc trò chuyện trước đây.\n");
        promptBuilder.append("Dưới đây là lịch sử trò chuyện giữa bạn (Trợ lý AI) và Người dùng.\n");
        promptBuilder.append("Hãy dựa vào lịch sử này để trả lời câu hỏi của Người dùng một cách chính xác.\n\n");
        promptBuilder.append("--- LỊCH SỬ TRÒ CHUYỆN ---\n");

        // Định dạng lại lịch sử để làm rõ vai trò của từng bên
        for (ChatMessage message : chatMemory.messages()) {
            String role = (message instanceof UserMessage) ? "Người dùng" : "Trợ lý AI";
            promptBuilder.append(String.format("%s: %s\n", role, message.text()));
        }

        promptBuilder.append("--- KẾT THÚC LỊCH SỬ ---\n\n");
        promptBuilder.append("Câu hỏi hiện tại của Người dùng: \"").append(userQuery).append("\"\n\n");
        promptBuilder.append("Câu trả lời của bạn (Trợ lý AI):");

        return promptBuilder.toString();
    }
}