package com.example.demo.model.chat;

import java.time.LocalDateTime;

import com.example.demo.model.auth.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class MemorySummaryLog {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String fullPrompt;

    @Column(columnDefinition = "TEXT")
    private String summaryResult;

    private LocalDateTime createdAt;
}
