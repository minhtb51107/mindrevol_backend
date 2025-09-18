package com.example.demo.service.chat;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatMessageRepository;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.service.chat.integration.OpenAIService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TitleGeneratorService {
    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final OpenAIService openAIService;

    public String generateAITitle(Long sessionId, User user) throws AccessDeniedException {
        ChatSession session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập session này");
        }

        // Lấy 5 tin nhắn gần nhất (đã sắp xếp theo thời gian)
        List<ChatMessage> recentMessages = messageRepo
            .findTop5ByChatSessionIdOrderByTimestampDesc(sessionId);

        // Tạo prompt cho AI
        StringBuilder prompt = new StringBuilder();
        prompt.append("Hãy tạo một tiêu đề ngắn gọn (3-5 từ) bằng tiếng Việt cho cuộc trò chuyện này. Tiêu đề nên phản ánh nội dung chính.\n\n");
        prompt.append("Nội dung trò chuyện:\n");
        
        for (ChatMessage msg : recentMessages) {
            String role = msg.getSender().equalsIgnoreCase("USER") ? "Người dùng" : "AI";
            prompt.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        // Tạo tin nhắn hệ thống và người dùng
        List<ChatMessage> promptMessages = Arrays.asList(
            ChatMessage.builder()
                .sender("system")
                .content("Bạn là trợ lý tạo tiêu đề hội thoại. Hãy trả lời chỉ bằng tiêu đề ngắn gọn (3-5 từ).")
                .build(),
            ChatMessage.builder()
                .sender("user")
                .content(prompt.toString())
                .build()
        );

        // Gọi OpenAI
        String aiTitle = openAIService.getChatCompletion_title(promptMessages);
        
        // Làm sạch kết quả
        aiTitle = cleanAITitle(aiTitle);
        
        // Cập nhật tiêu đề
        session.setTitle(aiTitle);
        sessionRepo.save(session);
        
        return aiTitle;
    }
    
    private String cleanAITitle(String rawTitle) {
        // Loại bỏ dấu ngoặc kép nếu có
        String cleaned = rawTitle.replace("\"", "").trim();
        
        // Giới hạn độ dài tối đa
        return cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
    }
}
