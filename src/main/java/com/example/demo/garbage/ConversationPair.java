// src/main/java/com/example/demo/model/chat/ConversationPair.java
package com.example.demo.garbage;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import com.example.demo.model.chat.ChatSession;

@Entity
@Table(name = "conversation_pairs")
@Data
public class ConversationPair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession chatSession;

    @Column(columnDefinition = "TEXT")
    private String userQuestion;

    @Column(columnDefinition = "TEXT")
    private String aiAnswer;

    private LocalDateTime timestamp = LocalDateTime.now();
    private int pairOrder;
}