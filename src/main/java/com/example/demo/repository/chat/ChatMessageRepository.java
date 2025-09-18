package com.example.demo.repository.chat;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatSession_IdOrderByTimestampAsc(Long sessionId);
    List<ChatMessage> findByChatSession_IdOrderByTimestampDesc(Long sessionId, Pageable pageable);
    void deleteByChatSession(ChatSession session);
    List<ChatMessage> findTop5ByChatSessionIdOrderByTimestampDesc(Long sessionId);
    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.chatSession.id = :sessionId")
    void deleteByChatSessionId(@Param("sessionId") Long sessionId);
    Page<ChatMessage> findByChatSessionIdOrderByTimestampDesc(Long sessionId, Pageable pageable);
 
}
