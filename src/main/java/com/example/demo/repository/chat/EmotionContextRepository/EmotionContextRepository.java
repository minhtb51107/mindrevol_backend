package com.example.demo.repository.chat.EmotionContextRepository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.chat.EmotionContext;


public interface EmotionContextRepository extends JpaRepository<EmotionContext, Long> {
    
    @Modifying
    @Query("DELETE FROM EmotionContext ec WHERE ec.chatSession.id = :sessionId")
    void deleteByChatSessionId(@Param("sessionId") Long sessionId);
    
    // Hoặc dùng method name query
    void deleteByChatSession_Id(Long sessionId);
    
    Optional<EmotionContext> findByChatSession_Id(Long sessionId);
}