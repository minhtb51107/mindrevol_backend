package com.example.demo.service.chat;

import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatMessageRepository;
import com.example.demo.repository.chat.ChatSessionRepository;
// import com.example.demo.service.chat.integration.OpenAIService; // 🔥 ĐÃ XÓA
import dev.langchain4j.model.chat.ChatLanguageModel; // ✅ THÊM MỚI

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TitleGeneratorService {
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    // private final OpenAIService openAIService; // 🔥 ĐÃ XÓA
    
    private final ChatLanguageModel chatLanguageModel; // ✅ THAY THẾ (Bean này đã có từ LangChain4jConfig)

    public String generateAITitle(Long sessionId, User user) throws AccessDeniedException {
        ChatSession session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập session này");
        }

        // Lấy 5 tin nhắn gần nhất (đã sắp xếp theo thời gian)
        List<ChatMessage> recentMessages = messageRepo
            .findTop5ByChatSessionIdOrderByTimestampDesc(sessionId);
        
        // Đảo ngược danh sách để có thứ tự chronological (cũ -> mới) cho AI
        Collections.reverse(recentMessages);

        // Tạo prompt cho AI
        StringBuilder conversationContext = new StringBuilder();
        conversationContext.append("Nội dung trò chuyện:\n");
        
        for (ChatMessage msg : recentMessages) {
            String role = msg.getSender().equalsIgnoreCase("USER") ? "Người dùng" : "AI";
            conversationContext.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        // ✅ TẠO PROMPT DUY NHẤT CHO LANGCHAIN4J
        String finalPrompt = "Bạn là trợ lý tạo tiêu đề. Hãy trả lời CHỈ bằng một tiêu đề thật ngắn gọn (tối đa 5 từ) bằng tiếng Việt cho cuộc trò chuyện sau. Không giải thích, không dùng dấu ngoặc kép.\n\n" 
                            + conversationContext.toString();

        try {
            // ✅ GỌI INTERFACE TRỪU TƯỢNG
            String aiTitle = chatLanguageModel.generate(finalPrompt);
            
            // Làm sạch kết quả
            aiTitle = cleanAITitle(aiTitle);
            
            // Cập nhật tiêu đề
            session.setTitle(aiTitle);
            sessionRepo.save(session);
            
            log.info("Đã tạo tiêu đề AI cho session {}: {}", sessionId, aiTitle);
            return aiTitle;

        } catch (Exception e) {
            // Lỗi này (ví dụ: API key sai) giờ sẽ được bắt lại một cách an toàn
            log.error("Không thể tạo tiêu đề AI do lỗi: {}", e.getMessage());
            // Trả về tiêu đề mặc định thay vì làm sập ứng dụng
            String defaultTitle = "Cuộc trò chuyện mới";
            session.setTitle(defaultTitle);
            sessionRepo.save(session);
            return defaultTitle;
        }
    }
    
    private String cleanAITitle(String rawTitle) {
        if (rawTitle == null) return "Cuộc trò chuyện";
        
        // Loại bỏ dấu ngoặc kép và các ký tự không mong muốn
        String cleaned = rawTitle.replace("\"", "")
                                 .replace("*", "")
                                 .replace("Tiêu đề:", "")
                                 .trim();
        
        // Giới hạn độ dài tối đa
        return cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
    }
}