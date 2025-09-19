//package com.example.demo.service.chat.memory;
//
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.model.chat.MemorySummaryResult;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class MemorySummarizerService {
//
//    @Autowired
//    private ChatLanguageModel chatLanguageModel;
//
//    // Phương thức chính để tóm tắt các tin nhắn hội thoại
//    public MemorySummaryResult summarize(List<ChatMessage> messages, String reason) {
//        try {
//            // Xây dựng context từ danh sách tin nhắn
//            StringBuilder context = new StringBuilder();
//            for (ChatMessage msg : messages) {
//                // Format: [sender]: [content]
//                context.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
//            }
//
//            // Thiết lập giới hạn độ dài cho bản tóm tắt
//            int minLength = 80; // tóm tắt tối thiểu (để đảm bảo đủ ý)
//            int maxLength = 400; // tóm tắt tối đa (để tránh quá dài dòng)
//
//            // System prompt hướng dẫn AI cách tóm tắt
//            String systemPrompt = "Bạn là trợ lý AI. Hãy tóm tắt cuộc trò chuyện sau, giữ lại các ý quan trọng, không bỏ sót thông tin cần thiết. Tóm tắt phải rõ ràng, đủ ý, không quá ngắn hoặc quá dài. Độ dài lý tưởng: 80-400 ký tự.";
//
//            // Xây dựng prompt để gửi đến AI
//            String fullPrompt = systemPrompt + "\n\nHãy tóm tắt cuộc trò chuyện sau:\n\n" + context.toString();
//
//            // Gọi ChatLanguageModel để lấy bản tóm tắt
//            String result = chatLanguageModel.generate(fullPrompt);
//            
//            // Ước lượng số token đã sử dụng
//            int tokenCount = estimateTokenCount(fullPrompt);
//
//            // Kiểm tra chất lượng tóm tắt - nếu quá ngắn
//            if (result.length() < minLength) {
//                // Nếu quá ngắn, yêu cầu AI tóm tắt lại chi tiết hơn
//                String retryPrompt = fullPrompt + "\n\nTóm tắt trên quá ngắn. Hãy tóm tắt lại chi tiết hơn, giữ đủ ý quan trọng.";
//                result = chatLanguageModel.generate(retryPrompt);
//            }
//            
//            // Kiểm tra nếu tóm tắt quá dài
//            if (result.length() > maxLength) {
//                // Cắt bớt nếu vượt quá giới hạn tối đa
//                result = result.substring(0, maxLength);
//            }
//
//            // Trả về kết quả tóm tắt với thông tin đầy đủ
//            return new MemorySummaryResult(result, tokenCount, reason);
//        } catch (Exception e) {
//            // Xử lý lỗi và trả về kết quả rỗng
//            System.err.println("Lỗi khi tóm tắt hội thoại: " + e.getMessage());
//            return new MemorySummaryResult("", 0, reason);
//        }
//    }
//
//    // Phương thức ước lượng số token (ước tính gần đúng)
//    private int estimateTokenCount(String prompt) {
//        // Ước lượng token bằng cách chia độ dài chuỗi cho 4 (cách ước lượng thô)
//        return prompt.length() / 4;
//    }
//}


