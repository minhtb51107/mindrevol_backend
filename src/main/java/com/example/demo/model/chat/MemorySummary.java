package com.example.demo.model.chat;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class MemorySummary {
    @Id @GeneratedValue private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;


    @Column(columnDefinition = "TEXT") private String summaryContent;
    @Column private LocalDateTime lastUpdated;
    @Column private int tokensUsed;
    @Column(columnDefinition = "TEXT") private String updateReason;
    @Column private String summaryType;

    // Mở rộng context
    @Column private Long lastMessageIdSummarized;
    @Column(columnDefinition = "TEXT") private String conversationGoal;
    @Column private String userPersona;
    @Column(columnDefinition = "TEXT") private String globalContext;
    @Column private String conversationStage;
    @Column private int topicSegment;
}

