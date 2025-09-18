// src/main/java/com/example/demo/model/chat/UserPreference.java
package com.example.demo.model.chat;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

import com.example.demo.model.auth.User;

@Entity
@Table(name = "user_preferences")
@Data
public class UserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @ElementCollection
    @CollectionTable(name = "preference_topics", joinColumns = @JoinColumn(name = "preference_id"))
    @MapKeyColumn(name = "topic")
    @Column(name = "interest_level")
    private Map<String, Double> favoriteTopics; // topic -> interest level (0.0-1.0)
    
    private String communicationStyle; // formal, casual, technical, simple
    private String detailPreference; // concise, detailed, balanced
    private String learningStyle; // visual, auditory, kinesthetic
    
    @ElementCollection
    @CollectionTable(name = "disliked_content", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "disliked_item")
    private List<String> dislikedContent;
}