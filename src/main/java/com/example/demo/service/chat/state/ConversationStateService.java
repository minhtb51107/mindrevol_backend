// src/main/java/com/example/demo/service/chat/state/ConversationStateService.java
package com.example.demo.service.chat.state;

import com.example.demo.model.chat.ChatSession;
import com.example.demo.model.chat.ConversationState;
import com.example.demo.model.chat.PendingAction;
import com.example.demo.repository.chat.ChatSessionRepository; // THÊM IMPORT NÀY
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationStateService {
    
    private final ConversationStateRepository stateRepository;
    private final ChatSessionRepository chatSessionRepository; // THÊM DEPENDENCY NÀY
    
    public ConversationState getOrCreateState(Long sessionId) {
        return stateRepository.findByChatSessionId(sessionId)
            .orElseGet(() -> createInitialState(sessionId));
    }
    
    public void updateState(Long sessionId, String newStage, String newTopic) {
        ConversationState state = getOrCreateState(sessionId);
        
        // Đảm bảo state history không null
        if (state.getStateHistory() == null) {
            state.setStateHistory(new java.util.ArrayList<>());
        }
        
        state.getStateHistory().add(state.getConversationStage());
        state.setConversationStage(newStage);
        state.setCurrentTopic(newTopic);
        state.setLastStateChange(LocalDateTime.now());
        
        stateRepository.save(state);
    }
    
    // ✅ THÊM PHƯƠNG THỨC MỚI: Cập nhật hành động đang chờ
    @Transactional
    public void updatePendingAction(Long sessionId, PendingAction action, String contextJson) {
        ConversationState state = getOrCreateState(sessionId);
        state.setPendingAction(action);
        state.setPendingActionContext(contextJson);
        stateRepository.save(state);
    }
    
    public void markNeedsClarification(Long sessionId, String question) {
        ConversationState state = getOrCreateState(sessionId);
        state.setNeedsClarification(true);
        state.setPendingQuestion(question);
        stateRepository.save(state);
    }
    
    public void clearClarification(Long sessionId) {
        ConversationState state = getOrCreateState(sessionId);
        state.setNeedsClarification(false);
        state.setPendingQuestion(null);
        stateRepository.save(state);
    }
    
    public void adjustFrustrationLevel(Long sessionId, int delta) {
        ConversationState state = getOrCreateState(sessionId);
        int newLevel = Math.min(Math.max(state.getFrustrationLevel() + delta, 0), 10);
        state.setFrustrationLevel(newLevel);
        stateRepository.save(state);
    }
    
    private ConversationState createInitialState(Long sessionId) {
        // ✅ SỬA LẠI: Lấy ChatSession từ database thay vì tạo mới
        ChatSession chatSession = chatSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("ChatSession not found with id: " + sessionId));
        
        ConversationState state = new ConversationState();
        state.setChatSession(chatSession); // ✅ Sử dụng chatSession từ database
        state.setConversationStage("greeting");
        state.setCurrentTopic("general");
        state.setFrustrationLevel(0);
        state.setSatisfactionScore(5);
        state.setPendingAction(PendingAction.NONE); // ✅ Đảm bảo giá trị mặc định được thiết lập
        state.setLastStateChange(LocalDateTime.now());
        state.setStateHistory(new java.util.ArrayList<>());
        state.setNeedsClarification(false);
        
        return stateRepository.save(state);
    }
    
    // ✅ THÊM PHƯƠNG THỨC TIỆN ÍCH
    public String getCurrentStage(Long sessionId) {
        return getOrCreateState(sessionId).getConversationStage();
    }
    
    public String getCurrentTopic(Long sessionId) {
        return getOrCreateState(sessionId).getCurrentTopic();
    }
    
    public int getFrustrationLevel(Long sessionId) {
        return getOrCreateState(sessionId).getFrustrationLevel();
    }
    
    public boolean needsClarification(Long sessionId) {
        return getOrCreateState(sessionId).getNeedsClarification();
    }
}