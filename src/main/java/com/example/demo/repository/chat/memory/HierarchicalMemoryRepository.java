package com.example.demo.repository.chat.memory;

import com.example.demo.model.chat.ChatSession;
import com.example.demo.model.chat.HierarchicalMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HierarchicalMemoryRepository extends JpaRepository<HierarchicalMemory, Long> {
    
    List<HierarchicalMemory> findByChatSessionOrderByHierarchyLevelDescSegmentStartAsc(ChatSession session);
    
    List<HierarchicalMemory> findByChatSessionAndHierarchyLevel(ChatSession session, int level);
    
    Optional<HierarchicalMemory> findByChatSessionAndHierarchyLevelAndSegmentStartAndSegmentEnd(
            ChatSession session, int level, int segmentStart, int segmentEnd);
    
    @Query("SELECT hm FROM HierarchicalMemory hm WHERE hm.chatSession = :session " +
           "AND hm.hierarchyLevel = 0 AND hm.segmentStart <= :segment AND hm.segmentEnd >= :segment")
    Optional<HierarchicalMemory> findLeafSummaryForSegment(
            @Param("session") ChatSession session, @Param("segment") int segment);
    
    @Query("SELECT hm FROM HierarchicalMemory hm WHERE hm.chatSession = :session " +
           "AND hm.hierarchyLevel = :level AND hm.segmentStart <= :segment AND hm.segmentEnd >= :segment")
    Optional<HierarchicalMemory> findSummaryForSegmentAndLevel(
            @Param("session") ChatSession session, @Param("level") int level, @Param("segment") int segment);
    
    void deleteByChatSession(ChatSession session);
    
    // ✅ THÊM PHƯƠNG THỨC MỚI
    Optional<HierarchicalMemory> findTopByChatSessionOrderByUpdatedAtDesc(ChatSession session);
    
    // ✅ CÓ THỂ THÊM THÊM CÁC PHƯƠNG THỨC TIỆN ÍCH KHÁC
    List<HierarchicalMemory> findByChatSessionOrderByUpdatedAtDesc(ChatSession session);
    
    @Query("SELECT hm FROM HierarchicalMemory hm WHERE hm.chatSession = :session " +
           "AND hm.updatedAt > :since")
    List<HierarchicalMemory> findByChatSessionAndUpdatedAtAfter(
            @Param("session") ChatSession session, @Param("since") java.time.LocalDateTime since);
    
    @Query("SELECT COUNT(hm) FROM HierarchicalMemory hm WHERE hm.chatSession = :session " +
           "AND hm.updatedAt > :since")
    long countByChatSessionAndUpdatedAtAfter(
            @Param("session") ChatSession session, @Param("since") java.time.LocalDateTime since);
}