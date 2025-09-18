package com.example.demo.service.chat;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatMessageRepository;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.service.chat.memory.CachedEmbeddingService;
import com.example.demo.service.chat.util.EmbeddingService;
//import com.example.demo.service.chat.vector.VectorStoreService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final CachedEmbeddingService cachedEmbeddingService; // Thêm service cache
    //private final VectorStoreService vectorStoreService; // Thêm dependency
    /**
     * Kiểm tra chuyển chủ đề giữa hai message cuối cùng bằng embedding
     * Trả về true nếu chủ đề thay đổi rõ rệt
     */
    public boolean isTopicShift(List<ChatMessage> messages, EmbeddingService embeddingService) {
        if (messages == null || messages.size() < 2) return false;
        ChatMessage prev = messages.get(messages.size() - 2);
        ChatMessage latest = messages.get(messages.size() - 1);
        try {
            boolean similar = embeddingService.isSimilar(prev.getContent(), latest.getContent());
            return !similar;
        } catch (Exception e) {
            // Nếu lỗi embedding, giả sử không chuyển chủ đề
            return false;
        }
    }
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepo;
    private final EmbeddingService embeddingService;
    
    public List<ChatMessage> getMessagesForSession(Long sessionId, User user) {
        ChatSession session = chatSessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền truy cập vào session này");
        }

        return chatMessageRepository.findByChatSession_IdOrderByTimestampAsc(sessionId);
    }
    
    public ChatMessage addMessage(Long sessionId, String role, String content, User user) {
        ChatSession session = chatSessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Không có quyền truy cập session này");
        }

        ChatMessage msg = ChatMessage.builder()
            .chatSession(session)
            .user(user)
            .sender(role)
            .content(content)
            .timestamp(LocalDateTime.now())
            .build();

        return chatMessageRepository.save(msg);
    }
    
    public List<ChatMessage> getRecentMessages(Long sessionId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessage> messages = chatMessageRepository.findByChatSession_IdOrderByTimestampDesc(sessionId, pageable);
        
        // ✅ ĐẢO NGƯỢC ĐỂ CÓ THỨ TỰ THỜI GIAN ĐÚNG (cũ -> mới)
        Collections.reverse(messages);
        
        return messages;
    }

    // ✅ THÊM PHƯƠNG THỨC MỚI ĐỂ DEBUG
    public List<ChatMessage> getRecentMessagesWithLog(Long sessionId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessage> messages = chatMessageRepository.findByChatSession_IdOrderByTimestampDesc(sessionId, pageable);
        
        log.debug("📩 Raw recent messages for session {} (limit {}):", sessionId, limit);
        messages.forEach(msg -> log.debug("   {}: {} - {}", 
            msg.getSender(), 
            msg.getContent(), 
            msg.getTimestamp()));
        
        // Đảo ngược để có thứ tự đúng
        Collections.reverse(messages);
        
        log.debug("📩 Reversed recent messages for session {}:", sessionId);
        messages.forEach(msg -> log.debug("   {}: {} - {}", 
            msg.getSender(), 
            msg.getContent(), 
            msg.getTimestamp()));
        
        return messages;
    }

    public ChatMessage saveMessage(ChatSession session, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setChatSession(session);
        message.setSender(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        
        ChatMessage savedMessage = chatMessageRepository.save(message);
        
        return savedMessage;
    }
    
    public void saveMessage(Long sessionId, String role, String content) {
        ChatSession session = chatSessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

        ChatMessage message = new ChatMessage();
        message.setChatSession(session);
        message.setSender(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());

        chatMessageRepository.save(message);
    }
    
    @Transactional
    public void deleteMessagesBySessionId(Long sessionId) {
        chatMessageRepository.deleteByChatSessionId(sessionId);
    }
}