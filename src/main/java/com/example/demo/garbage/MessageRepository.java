//package com.example.demo.repository;
//
//import java.util.List;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import com.example.demo.model.ChatMessage; // ✅ import đúng
//
//@Repository
//public interface MessageRepository extends JpaRepository<ChatMessage, Long> {
//    List<ChatMessage> findAllByChatSession_IdOrderByTimestampAsc(Long sessionId);
//}
//
