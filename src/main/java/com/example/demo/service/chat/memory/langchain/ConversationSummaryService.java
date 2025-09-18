package com.example.demo.service.chat.memory.langchain;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationSummaryService {

    private final ChatLanguageModel chatLanguageModel;

    public ConversationSummaryService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public String generateSummary(List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "Chưa có nội dung để tóm tắt.";
        }
        
        // Build context from messages
        StringBuilder context = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            String role = message instanceof UserMessage ? "User" : "Assistant";
            context.append(role).append(": ").append(message.text()).append("\n");
        }
        
        // Create summary using LC4J
        String summaryPrompt = "Bạn là trợ lý tóm tắt hội thoại. Hãy tóm tắt ngắn gọn cuộc trò chuyện sau:\n\n" + context.toString();
        
        try {
            return chatLanguageModel.generate(summaryPrompt);
        } catch (Exception e) {
            return "Tóm tắt: Cuộc trò chuyện đang diễn ra về các chủ đề đa dạng.";
        }
    }
}