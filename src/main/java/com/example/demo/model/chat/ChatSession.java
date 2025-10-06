package com.example.demo.model.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.model.auth.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter; // ✅ SỬ DỤNG
import lombok.NoArgsConstructor;
import lombok.Setter; // ✅ SỬ DỤNG
import lombok.ToString; // ✅ THÊM IMPORT

@Entity
@Getter // ✅ THAY THẾ @Data
@Setter // ✅ THAY THẾ @Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude // ✅ DÒNG QUAN TRỌNG NHẤT: Bỏ qua thuộc tính này khi tạo toString()
    private User user;

    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;
    
    // ... (các phương thức @PrePersist, @PreUpdate và các collection khác giữ nguyên)
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude // Thêm vào đây để đảm bảo an toàn
    private List<ChatMessage> messages = new ArrayList<>();
    
    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude // Thêm vào đây để đảm bảo an toàn
    private List<ConversationState> conversationStates = new ArrayList<>();

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude // Thêm vào đây để đảm bảo an toàn
    private List<EmotionContext> emotionContexts = new ArrayList<>();
}