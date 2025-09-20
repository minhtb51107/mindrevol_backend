package com.example.demo.repository.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;


@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
	List<ChatSession> findByUser(User user);
	
	// <-- Thêm phương thức mới để tìm các session được cập nhật gần đây
    List<ChatSession> findByUpdatedAtAfter(LocalDateTime dateTime);
}

