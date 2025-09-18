package com.example.demo.service.chat.context;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.model.chat.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressionService {
    
    private final ChatLanguageModel chatLanguageModel;
    
    public String compressContext(List<ChatMessage> messages, String currentQuery) {
        if (messages.isEmpty()) return "";
        
        // Xây dựng prompt cho LLM tóm tắt
        StringBuilder contextBuilder = new StringBuilder();
        for (ChatMessage msg : messages) {
            contextBuilder.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }
        
        String context = contextBuilder.toString();
        
        // Nếu context ngắn thì không cần nén
        if (context.length() < 1000) {
            return context;
        }
        
        try {
            String systemPrompt = "Bạn là trợ lý tóm tắt ngữ cảnh. Hãy tóm tắt đoạn hội thoại sau thành một bản tóm tắt ngắn gọn, " +
                    "giữ lại thông tin quan trọng nhất liên quan đến câu hỏi hiện tại: '" + currentQuery + "'." +
                    "Chỉ trả về bản tóm tắt, không thêm giải thích.";
            
            String userPrompt = context;
            
            // Sử dụng ChatLanguageModel để gọi API
            String compressedContext = chatLanguageModel.generate(
                systemPrompt + "\n\n" + userPrompt
            );
            
            return compressedContext;
            
        } catch (Exception e) {
            log.warn("Context compression failed, using fallback", e);
            // Fallback: lấy các message quan trọng nhất
            return getImportantMessages(messages, currentQuery);
        }
    }
    
    private String getImportantMessages(List<ChatMessage> messages, String query) {
        // Ưu tiên các message có similarity score cao
        return messages.stream()
            .filter(msg -> msg.getSimilarityScore() != null && msg.getSimilarityScore() > 0.7)
            .map(msg -> msg.getSender() + ": " + msg.getContent())
            .collect(Collectors.joining("\n"));
    }
}