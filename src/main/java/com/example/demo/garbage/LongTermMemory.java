package com.example.demo.garbage;

import java.time.LocalDateTime;

import com.example.demo.model.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "long_term_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LongTermMemory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    private String key; // ví dụ: goal, style, project

    @Column(columnDefinition = "TEXT")
    private String value;

    private LocalDateTime updatedAt;

    // getter, setter, builder...
}

