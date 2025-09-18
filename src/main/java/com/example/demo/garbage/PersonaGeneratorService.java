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
//public class PersonaGeneratorService {
//    private final OpenAIService openAIService;
//
//    public String generatePersona(List<ChatMessage> history) {
//        StringBuilder ctx = new StringBuilder();
//        history.forEach(m -> ctx.append(m.getSender()).append(": ").append(m.getContent()).append("\n"));
//        List<Map<String,String>> prompt = List.of(
//            Map.of("role","system","content","Bạn là trợ lý giúp xác định tính cách người dùng từ lịch sử trò chuyện."),
//            Map.of("role","user","content",
//              "Dựa trên đoạn hội thoại sau, tóm tắt thành 1 câu: “Người dùng là …”:\n\n" + ctx.toString()
//            )
//        );
//        return openAIService.getChatCompletion(prompt, "gpt-3.5-turbo", 120).trim();
//    }
//}
//
