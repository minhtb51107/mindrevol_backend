package com.example.demo.repository.chat.ConversationStateRepository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.chat.ConversationState;


//ConversationStateRepository
public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {
 Optional<ConversationState> findByChatSessionId(Long sessionId);
 
 @Modifying
 @Query("DELETE FROM ConversationState cs WHERE cs.chatSession.id = :sessionId")
 void deleteByChatSessionId(@Param("sessionId") Long sessionId);
 
 // Hoặc
 void deleteByChatSession_Id(Long sessionId);
}