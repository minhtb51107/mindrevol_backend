// Khai báo package và import các thư viện cần thiết
package com.example.demo.service.chat.memory;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.MemorySummaryResult;
import com.example.demo.service.chat.integration.OpenAIService;

// Đánh dấu đây là một Spring Service
@Service
public class MemorySummarizerService {

    // Inject OpenAIService để gọi API OpenAI
    @Autowired
    private OpenAIService openAIService;

    // Phương thức chính để tóm tắt các tin nhắn hội thoại
    public MemorySummaryResult summarize(List<ChatMessage> messages, String reason) {
        try {
            // Xây dựng context từ danh sách tin nhắn
            StringBuilder context = new StringBuilder();
            for (ChatMessage msg : messages) {
                // Format: [sender]: [content]
                context.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
            }

            // Thiết lập giới hạn độ dài cho bản tóm tắt
            int minLength = 80; // tóm tắt tối thiểu (để đảm bảo đủ ý)
            int maxLength = 400; // tóm tắt tối đa (để tránh quá dài dòng)

            // System prompt hướng dẫn AI cách tóm tắt
            String systemPrompt = "Bạn là trợ lý AI. Hãy tóm tắt cuộc trò chuyện sau, giữ lại các ý quan trọng, không bỏ sót thông tin cần thiết. Tóm tắt phải rõ ràng, đủ ý, không quá ngắn hoặc quá dài. Độ dài lý tưởng: 80-400 ký tự.";

            // Xây dựng prompt để gửi đến AI
            List<Map<String, String>> prompt = List.of(
                Map.of("role", "system", "content", systemPrompt), // Vai trò hệ thống
                Map.of("role", "user", "content", context.toString()) // Nội dung cần tóm tắt
            );

            // Gọi API OpenAI để lấy bản tóm tắt (sử dụng GPT-3.5-turbo để tiết kiệm chi phí)
            String result = openAIService.getChatCompletion(prompt, "gpt-3.5-turbo", 200);
            // Ước lượng số token đã sử dụng
            int tokenCount = estimateTokenCount(prompt);

            // Kiểm tra chất lượng tóm tắt - nếu quá ngắn
            if (result.length() < minLength) {
                // Nếu quá ngắn, yêu cầu AI tóm tắt lại chi tiết hơn
                String retryPrompt = "Tóm tắt trên quá ngắn. Hãy tóm tắt lại chi tiết hơn, giữ đủ ý quan trọng.";
                List<Map<String, String>> retry = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", context.toString() + "\n" + retryPrompt)
                );
                // Gọi lại API với yêu cầu chi tiết hơn
                result = openAIService.getChatCompletion(retry, "gpt-3.5-turbo", 250);
            }
            
            // Kiểm tra nếu tóm tắt quá dài
            if (result.length() > maxLength) {
                // Cắt bớt nếu vượt quá giới hạn tối đa
                result = result.substring(0, maxLength);
            }

            // Trả về kết quả tóm tắt với thông tin đầy đủ
            return new MemorySummaryResult(result, tokenCount, reason);
        } catch (Exception e) {
            // Xử lý lỗi và trả về kết quả rỗng
            System.err.println("Lỗi khi tóm tắt hội thoại: " + e.getMessage());
            return new MemorySummaryResult("", 0, reason);
        }
    }

    // Phương thức ước lượng số token (ước tính gần đúng)
    private int estimateTokenCount(List<Map<String, String>> prompt) {
        // Ước lượng token bằng cách chia độ dài chuỗi cho 4 (cách ước lượng thô)
        return prompt.stream().mapToInt(m -> m.get("content").length() / 4).sum();
    }
}