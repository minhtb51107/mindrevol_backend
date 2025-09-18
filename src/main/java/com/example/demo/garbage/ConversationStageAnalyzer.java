//package com.example.demo.service.chat;
//
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.stereotype.Component;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.model.chat.ConversationStage;
//import com.example.demo.service.chat.integration.OpenAIService;
//
//import lombok.RequiredArgsConstructor;
//
//@Component
//@RequiredArgsConstructor
//public class ConversationStageAnalyzer {
//
//    private final OpenAIService openAIService;
//
//    public ConversationStage analyze(List<ChatMessage> messages) {
//        StringBuilder history = new StringBuilder();
//        for (ChatMessage msg : messages) {
//            history.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
//        }
//
//        List<Map<String, String>> prompt = List.of(
//            Map.of("role", "system", "content", "Bạn là một trợ lý AI chuyên phân tích cuộc trò chuyện."),
//            Map.of("role", "user", "content",
//                "Dưới đây là đoạn hội thoại. Hãy xác định nó đang ở giai đoạn nào trong các giai đoạn sau:\n\n" +
//                "INTRO, BRAINSTORMING, SOLUTION, CONCLUSION, REFINEMENT, TOPIC_SHIFT\n\n" +
//                "Chỉ trả về đúng 1 từ là tên giai đoạn (in hoa, không mô tả thêm).\n\n" + history.toString())
//        );
//
//        try {
//            String result = openAIService.getChatCompletion(prompt, "gpt-3.5-turbo", 50).trim().toUpperCase();
//            
//            return ConversationStage.valueOf(result);
//        } catch (Exception e) {
//            return ConversationStage.UNKNOWN;
//        }
//    }
//}


