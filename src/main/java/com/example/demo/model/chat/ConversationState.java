// src/main/java/com/example/demo/model/chat/ConversationState.java
package com.example.demo.model.chat;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "conversation_states")
@Data
public class ConversationState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession chatSession;
    
    private String currentIntent;
    private String currentTopic;
    private String conversationStage; // greeting, main, closing, followup
    
    private Boolean needsClarification = false;
    private String pendingQuestion;
    
    private Integer frustrationLevel = 0; // 0-10
    private Integer satisfactionScore = 5; // 1-10
    
    private LocalDateTime lastStateChange;
    
    @ElementCollection(fetch = FetchType.EAGER) // ✅ THAY ĐỔI THÀNH EAGER
    @CollectionTable(name = "state_history", joinColumns = @JoinColumn(name = "state_id"))
    @Column(name = "previous_state")
    private List<String> stateHistory;
}