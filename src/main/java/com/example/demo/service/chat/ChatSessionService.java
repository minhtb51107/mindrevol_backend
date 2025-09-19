package com.example.demo.service.chat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatMessageRepository;
import com.example.demo.repository.chat.ChatSessionRepository;
//import com.example.demo.repository.chat.MessageEmbeddingRepository;
//import com.example.demo.repository.chat.TextChunkRepository;
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
//import com.example.demo.repository.chat.memory.MemorySummaryLogRepo;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepo;
    //private final MemorySummaryLogRepo memorySummaryLogRepo;
    private final ConversationStateRepository conversationStateRepo; // ✅ THÊM
    private final EmotionContextRepository emotionContextRepo; // ✅ THÊM
//    private final TextChunkRepository chunkRepository;
//    private final MessageEmbeddingRepository embeddingRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatSession createSession(User user, String title) {
        ChatSession session = ChatSession.builder()
            .title(title)
            .user(user)
            .createdAt(LocalDateTime.now())
            .build();
        return chatSessionRepo.save(session);
    }

    public List<ChatSession> getSessionsForUser(User user) {
        return chatSessionRepo.findByUser(user);
    }

 // Trong ChatSessionService.java
    public boolean hasAccessToSession(Long sessionId, User user) {
        if (sessionId == null || user == null) {
            return false;
        }
        
        Optional<ChatSession> sessionOptional = chatSessionRepo.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return false;
        }
        
        ChatSession session = sessionOptional.get();
        return session.getUser() != null && session.getUser().getId().equals(user.getId());
    }

    // Hoặc phương thức này để trả về session nếu có quyền
    public Optional<ChatSession> getSessionIfAccessible(Long sessionId, User user) {
        if (sessionId == null || user == null) {
            return Optional.empty();
        }
        
        Optional<ChatSession> sessionOptional = chatSessionRepo.findById(sessionId);
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }
        
        ChatSession session = sessionOptional.get();
        if (session.getUser() != null && session.getUser().getId().equals(user.getId())) {
            return Optional.of(session);
        }
        
        return Optional.empty();
    }
    
    @Transactional
    public void deleteSession(Long sessionId, User user) {
        ChatSession session = chatSessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Không có quyền xóa session này");
        }

        // 1. Xóa memory summary logs
        //memorySummaryLogRepo.deleteAll(memorySummaryLogRepo.findBySession(session));

        // 2. Xóa conversation states và emotion contexts
        conversationStateRepo.deleteByChatSessionId(sessionId);
        emotionContextRepo.deleteByChatSessionId(sessionId);

//        // 3. Xóa message embeddings trước
//        embeddingRepository.deleteByChatSessionId(sessionId);
//
//        // 4. Xóa text chunks
//        chunkRepository.deleteByChatSessionId(sessionId);

        // 5. Xóa messages
        chatMessageRepository.deleteByChatSessionId(sessionId);

        // 6. Cuối cùng xóa session
        chatSessionRepo.delete(session);
    }

    public void updateSessionTitle(Long sessionId, String newTitle, User user) {
        ChatSession session = chatSessionRepo.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to edit this session");
        }

        session.setTitle(newTitle);
        chatSessionRepo.save(session);
    }
}