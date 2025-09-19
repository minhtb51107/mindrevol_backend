package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemoryQueryStep implements RagStep {

    @Override
    public RagContext execute(RagContext context) {
        String reply = handleMemoryQuestion(context.getChatMemory().messages(), context.getInitialQuery());
        context.setReply(reply);
        return context;
    }

    // Di chuyển logic private từ ChatAIService
    private String handleMemoryQuestion(List<ChatMessage> messages, String currentPrompt) {
        if (messages.isEmpty()) {
            return "Chúng ta chưa có cuộc trò chuyện nào trước đó.";
        }

        List<String> userMessages = messages.stream()
            .filter(msg -> msg instanceof UserMessage)
            .map(ChatMessage::text)
            .filter(msg -> !msg.equals(currentPrompt)) 
            .collect(Collectors.toList());

        if (userMessages.isEmpty()) {
            return "Tôi chưa nhận được tin nhắn nào từ bạn trước đây.";
        }

        String lastUserMessage = userMessages.get(userMessages.size() - 1);

        return "Bạn vừa nhắn: \"" + lastUserMessage + "\". " +
               "Bạn muốn tôi giải thích thêm hay có câu hỏi gì về điều này không?";
    }
}