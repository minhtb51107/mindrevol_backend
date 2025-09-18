//package com.example.demo.service;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.example.demo.dto.chat.ChatMessageDTO;
//import com.example.demo.model.ChatMessage;
//import com.example.demo.model.ChatSession;
//import com.example.demo.model.User;
//import com.example.demo.repository.ChatMessageRepository;
//import com.example.demo.repository.ChatSessionRepository;
//
//import lombok.RequiredArgsConstructor;
//
//@Service
//@RequiredArgsConstructor
//public class ChatHistoryService {
//
//    private final ChatSessionRepository chatSessionRepo;
//    private final ChatMessageRepository chatMessageRepository;
//    private final OpenAIService openAIService;
//    private final LongTermMemoryService longTermMemoryService;
//    //private final EnhancedOpenAIService enhancedOpenAIService;
//
//    public ChatSession createSession(User user, String title) {
//        ChatSession session = ChatSession.builder()
//            .title(title)
//            .user(user)
//            .createdAt(LocalDateTime.now())
//            .build();
//        return chatSessionRepo.save(session);
//    }
//
//    public List<ChatMessage> getMessagesForSession(Long sessionId, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new AccessDeniedException("Bạn không có quyền truy cập vào session này");
//        }
//
//        return chatMessageRepository.findByChatSession_IdOrderByTimestampAsc(sessionId);
//    }
//
//    public List<ChatSession> getSessionsForUser(User user) {
//        return chatSessionRepo.findByUser(user);
//    }
//
//    public ChatMessage addMessage(Long sessionId, String role, String content, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new AccessDeniedException("Không có quyền truy cập session này");
//        }
//
//        ChatMessage msg = ChatMessage.builder()
//            .chatSession(session)
//            .user(user)
//            .sender(role)
//            .content(content)
//            .timestamp(LocalDateTime.now())
//            .build();
//
//        return chatMessageRepository.save(msg);
//    }
//
//    @Transactional
//    public void deleteSession(Long sessionId, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new AccessDeniedException("Không có quyền xóa session này");
//        }
//
//        chatMessageRepository.deleteByChatSession(session);
//        chatSessionRepo.deleteById(sessionId);
//    }
//
//    public void updateSessionTitle(Long sessionId, String newTitle, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new RuntimeException("Session not found"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new RuntimeException("Unauthorized to edit this session");
//        }
//
//        session.setTitle(newTitle);
//        chatSessionRepo.save(session);
//    }
//
//    public List<ChatMessage> getRecentMessages(Long sessionId, int limit) {
//        Pageable pageable = PageRequest.of(0, limit);
//        return chatMessageRepository.findByChatSession_IdOrderByTimestampDesc(sessionId, pageable);
//    }
//
//    public void saveMessage(Long sessionId, String role, String content) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//
//        ChatMessage message = new ChatMessage();
//        message.setChatSession(session);
//        message.setSender(role);
//        message.setContent(content);
//        message.setTimestamp(LocalDateTime.now());
//
//        chatMessageRepository.save(message);
//    }
//
//    public String processMessages(Long sessionId, List<ChatMessageDTO> messageDTOs, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//
//        ChatMessageDTO currentInput = messageDTOs.get(messageDTOs.size() - 1);
//        String prompt = currentInput.getContent();
//
//        List<ChatMessage> recentMessages = getRecentMessages(sessionId, 50);
//
//        // ✅ Dùng danh sách có thể thay đổi
//        List<ChatMessage> context = new ArrayList<>(
//            openAIService.getRelevantMessages(recentMessages, prompt, 6)
//        );
//
//        // ✅ Gắn trí nhớ dài hạn vào đầu prompt (nếu có)
//        Map<String, String> memoryMap = longTermMemoryService.getMemoryMap(user);
//        if (!memoryMap.isEmpty()) {
//            StringBuilder memoryContext = new StringBuilder("Thông tin nhớ về người dùng:\n");
//            memoryMap.forEach((k, v) -> memoryContext.append("- ").append(k).append(": ").append(v).append("\n"));
//
//            context.add(0, ChatMessage.builder()
//                .sender("system")
//                .content(memoryContext.toString())
//                .build());
//        }
//
//        // ✅ Lưu tin nhắn người dùng mới nhất
//        saveMessage(sessionId, currentInput.getRole(), prompt);
//
//        // ✅ Gắn thêm vào context để gửi cho AI
//        ChatMessage userMsg = ChatMessage.builder()
//            .chatSession(session)
//            .sender(currentInput.getRole())
//            .content(prompt)
//            .timestamp(LocalDateTime.now())
//            .build();
//        context.add(userMsg);
//
//        // ✅ Gọi OpenAI để lấy phản hồi
//        String reply = openAIService.getChatCompletion(context);
//
//        // ✅ Lưu phản hồi AI
//        saveMessage(sessionId, "assistant", reply);
//
//        return reply;
//    }
//
//    
// // ✅ Lấy tin nhắn theo session
//    public List<ChatMessage> getMessages(Long sessionId, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session not found"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new AccessDeniedException("Không có quyền truy cập session này");
//        }
//
//        return chatMessageRepository.findByChatSession_IdOrderByTimestampAsc(sessionId);
//    }
//    
//    
//    public String generateAITitle(Long sessionId, User user) {
//        ChatSession session = chatSessionRepo.findById(sessionId)
//            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//
//        if (!session.getUser().getId().equals(user.getId())) {
//            throw new AccessDeniedException("Không có quyền truy cập session này");
//        }
//
//        // Lấy 5 tin nhắn gần nhất (đã sắp xếp theo thời gian)
//        List<ChatMessage> recentMessages = chatMessageRepository
//            .findTop5ByChatSessionIdOrderByTimestampDesc(sessionId);
//
//        // Tạo prompt cho AI
//        StringBuilder prompt = new StringBuilder();
//        prompt.append("Hãy tạo một tiêu đề ngắn gọn (3-5 từ) bằng tiếng Việt cho cuộc trò chuyện này. Tiêu đề nên phản ánh nội dung chính.\n\n");
//        prompt.append("Nội dung trò chuyện:\n");
//        
//        for (ChatMessage msg : recentMessages) {
//            String role = msg.getSender().equalsIgnoreCase("USER") ? "Người dùng" : "AI";
//            prompt.append(role).append(": ").append(msg.getContent()).append("\n");
//        }
//
//        // Tạo tin nhắn hệ thống và người dùng
//        List<ChatMessage> promptMessages = Arrays.asList(
//            ChatMessage.builder()
//                .sender("system")
//                .content("Bạn là trợ lý tạo tiêu đề hội thoại. Hãy trả lời chỉ bằng tiêu đề ngắn gọn (3-5 từ).")
//                .build(),
//            ChatMessage.builder()
//                .sender("user")
//                .content(prompt.toString())
//                .build()
//        );
//
//        // Gọi OpenAI
//        String aiTitle = openAIService.getChatCompletion_title(promptMessages);
//        
//        // Làm sạch kết quả
//        aiTitle = cleanAITitle(aiTitle);
//        
//        // Cập nhật tiêu đề
//        session.setTitle(aiTitle);
//        chatSessionRepo.save(session);
//        
//        return aiTitle;
//    }
//
//    private String cleanAITitle(String rawTitle) {
//        // Loại bỏ dấu ngoặc kép nếu có
//        String cleaned = rawTitle.replace("\"", "").trim();
//        
//        // Giới hạn độ dài tối đa
//        return cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
//    }
//}


