//package com.example.demo.garbage;
//
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.stereotype.Service;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.service.chat.integration.OpenAIService;
//
//import lombok.RequiredArgsConstructor;
//
//@Service
//@RequiredArgsConstructor
//public class GoalGeneratorService {
//    private final OpenAIService openAIService;
//
//    public String generateGoal(List<ChatMessage> history) {
//        StringBuilder ctx = new StringBuilder();
//        history.forEach(m -> ctx.append(m.getSender()).append(": ").append(m.getContent()).append("\n"));
//        List<Map<String,String>> prompt = List.of(
//           Map.of("role","system","content","Bạn là trợ lý tóm tắt mục tiêu cuộc trò chuyện."),
//           Map.of("role","user","content",
//             "Dựa vào đoạn hội thoại, người dùng đang hướng tới mục tiêu gì? Trả về 1–2 từ ngắn gọn."
//             + "\n\n" + ctx.toString()
//           )
//        );
//        return openAIService.getChatCompletion(prompt, "gpt-3.5-turbo", 50).trim();
//    }
//}
