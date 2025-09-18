package com.example.demo.repository.chat;

import com.example.demo.model.chat.TextChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TextChunkRepository extends JpaRepository<TextChunk, Long> {
    
    List<TextChunk> findByOriginalMessageId(Long messageId);
    
    List<TextChunk> findByChatSessionId(Long sessionId);
    
    @Query("SELECT COUNT(tc) FROM TextChunk tc WHERE tc.originalMessage.id = :messageId")
    int countByMessageId(@Param("messageId") Long messageId);
    
    @Query("SELECT tc FROM TextChunk tc WHERE tc.originalMessage.id = :messageId ORDER BY tc.chunkIndex ASC")
    List<TextChunk> findByMessageIdOrdered(@Param("messageId") Long messageId);
    
    void deleteByOriginalMessageId(Long messageId);
    
    void deleteByChatSessionId(Long sessionId);
    
    @Query("SELECT AVG(tc.tokenCount) FROM TextChunk tc WHERE tc.originalMessage.id = :messageId")
    Double getAverageTokenCountByMessageId(@Param("messageId") Long messageId);
}