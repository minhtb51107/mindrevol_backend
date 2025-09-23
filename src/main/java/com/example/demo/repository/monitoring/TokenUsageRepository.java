package com.example.demo.repository.monitoring;

import com.example.demo.model.monitoring.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {
}