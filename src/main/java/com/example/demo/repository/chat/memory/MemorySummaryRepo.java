//
//package com.example.demo.repository.chat.memory;
//
//import java.util.Optional;
//import java.util.List;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.model.chat.MemorySummary;
//
//public interface MemorySummaryRepo extends JpaRepository<MemorySummary, Long> {
//    Optional<MemorySummary> findByChatSession(ChatSession session);
//    void deleteByChatSession(ChatSession chatSession);
//
//    // Truy vấn tóm tắt theo session và segment
//    Optional<MemorySummary> findByChatSessionAndTopicSegment(ChatSession session, int topicSegment);
//
//    // Truy vấn tóm tắt mới nhất của một phiên chat
//    Optional<MemorySummary> findTopByChatSessionOrderByLastUpdatedDesc(ChatSession session);
//
//    // Truy vấn tất cả tóm tắt theo session, sắp xếp theo topicSegment tăng dần
//    List<MemorySummary> findAllByChatSessionOrderByTopicSegmentAsc(ChatSession session);
//    
// // Trong MemorySummaryRepo
//    @Query("SELECT ms FROM MemorySummary ms WHERE ms.chatSession = :session AND ms.topicSegment BETWEEN :start AND :end")
//    List<MemorySummary> findByChatSessionAndTopicSegmentBetween(
//        @Param("session") ChatSession session, 
//        @Param("start") int start, 
//        @Param("end") int end);
//}
//
