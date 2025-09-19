package com.example.demo.model.chat;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.example.demo.model.auth.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp = LocalDateTime.now();
    
    @Transient
    private Double similarityScore;

    // Thêm field embedding để lưu precomputed embeddings
    @ElementCollection
    @CollectionTable(name = "message_embeddings", 
                    joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "embedding_value")
    private List<Double> embedding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id")
    private ChatSession chatSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
//    @OneToMany(mappedBy = "originalMessage", cascade = CascadeType.ALL, orphanRemoval = true)
//    @JsonIgnore
//    private List<TextChunk> textChunks = new ArrayList<>();

    public ChatMessage(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
}
