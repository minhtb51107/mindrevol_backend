package com.example.demo.model.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.model.chat.ChatSession;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter; // ✅ SỬ DỤNG
import lombok.NoArgsConstructor;
import lombok.Setter; // ✅ SỬ DỤNG
import lombok.ToString; // ✅ THÊM IMPORT

@Entity
@Table(name = "users")
@Getter // ✅ THAY THẾ @Data
@Setter // ✅ THAY THẾ @Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... (các thuộc tính khác giữ nguyên)
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    private String username;
    private String fullName;
    private Integer age;
    private String currentSkillLevel;
    private String learningGoal;
    private String availableTimePerWeek;
    private String preferredLanguage;
    private String experienceBackground;
    private String reasonToJoin;
    private String referrer;
    private LocalDateTime registeredAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude // ✅ DÒNG QUAN TRỌNG NHẤT: Bỏ qua thuộc tính này khi tạo toString()
    private List<ChatSession> sessions = new ArrayList<>();
}