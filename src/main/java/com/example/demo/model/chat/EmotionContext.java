// src/main/java/com/example/demo/model/chat/EmotionContext.java
package com.example.demo.model.chat;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

import com.example.demo.model.auth.User;

@Entity
@Table(name = "emotion_contexts")
@Data
public class EmotionContext {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession chatSession;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    private String currentEmotion; // happy, sad, angry, neutral, excited
    private Double emotionIntensity; // 0.0 to 1.0
    
    @ElementCollection
    @CollectionTable(name = "emotion_history", joinColumns = @JoinColumn(name = "emotion_context_id"))
    @MapKeyColumn(name = "timestamp")
    @Column(name = "emotion_score")
    private Map<LocalDateTime, String> emotionHistory;
    
    private LocalDateTime lastUpdated;
}