// src/main/java/com/example/demo/model/monitoring/ExternalServiceUsage.java
package com.example.demo.model.monitoring;

import com.example.demo.model.auth.User;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
public class ExternalServiceUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serviceName; // e.g., "COHERE_RERANK", "SERPER_SEARCH"
    private String usageUnit;   // e.g., "documents", "queries"
    private Long usageAmount;   // e.g., 100 documents, 1 query
    private BigDecimal cost;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}