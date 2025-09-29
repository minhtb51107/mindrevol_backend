package com.example.demo.model.monitoring;

import com.example.demo.model.auth.User; // Thêm import này
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- SỬA ĐỔI Ở ĐÂY ---
    // Thay vì Long userId, chúng ta tạo mối quan hệ Many-to-One
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Tên cột trong database vẫn là user_id
    private User user;
    // --- KẾT THÚC SỬA ĐỔI ---
    
    private String callIdentifier; // <-- THÊM TRƯỜNG NÀY

    private Long sessionId;

    private String modelName;

    private int inputTokens;

    private int outputTokens;

    // --- THÊM TRƯỜNG MỚI ---
    private int totalTokens;
    // --- KẾT THÚC THÊM ---

    @Column(precision = 19, scale = 10)
    private BigDecimal cost;

    // Đổi tên 'timestamp' thành 'createdAt' cho nhất quán
    private LocalDateTime createdAt;
}