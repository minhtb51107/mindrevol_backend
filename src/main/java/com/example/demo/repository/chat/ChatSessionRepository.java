package com.example.demo.repository.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;


@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
	List<ChatSession> findByUser(User user);
}

