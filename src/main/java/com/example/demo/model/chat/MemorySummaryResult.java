package com.example.demo.model.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemorySummaryResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Thêm trường ID này
    
    @Column
    private String content;
    @Column
    private int tokensUsed;
    @Column
    private String reason;
    @Column
    private String promptUsed;

    // Constructor không cần id (để tiện sử dụng)
    public MemorySummaryResult(String content, int tokensUsed, String reason) {
        this.content = content;
        this.tokensUsed = tokensUsed;
        this.reason = reason;
    }
}