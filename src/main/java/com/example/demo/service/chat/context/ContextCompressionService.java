package com.example.demo.service.chat.context;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.service.chat.ChatAIService;
import com.example.demo.service.chat.integration.OpenAIService;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//src/main/java/com/example/service/chat/context/ContextCompressionService.java
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressionService {
 
 private final OpenAIService openAIService;
 
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
         List<Map<String, String>> compressionPrompt = List.of(
             Map.of("role", "system", "content", 
                 "Bạn là trợ lý tóm tắt ngữ cảnh. Hãy tóm tắt đoạn hội thoại sau thành một bản tóm tắt ngắn gọn, " +
                 "giữ lại thông tin quan trọng nhất liên quan đến câu hỏi hiện tại: '" + currentQuery + "'." +
                 "Chỉ trả về bản tóm tắt, không thêm giải thích."),
             Map.of("role", "user", "content", context)
         );
         
         return openAIService.getChatCompletion(compressionPrompt, "gpt-3.5-turbo", 300);
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