package com.example.demo.model.monitoring;

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

    private Long userId;

    private Long sessionId;

    private String modelName;

    private int inputTokens;

    private int outputTokens;

    @Column(precision = 19, scale = 10)
    private BigDecimal cost;

    private LocalDateTime timestamp;
}