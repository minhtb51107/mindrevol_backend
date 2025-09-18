// src/main/java/com/example/demo/repository/chat/ConversationPairRepository.java
package com.example.demo.garbage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationPairRepository extends JpaRepository<ConversationPair, Long> {
    List<ConversationPair> findByChatSessionIdOrderByPairOrderDesc(Long sessionId);
    
    @Query("SELECT cp FROM ConversationPair cp WHERE cp.chatSession.id = :sessionId ORDER BY cp.pairOrder DESC LIMIT :limit")
    List<ConversationPair> findTopBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);
    
    void deleteByChatSessionIdAndPairOrderLessThan(Long sessionId, int minOrder);
}