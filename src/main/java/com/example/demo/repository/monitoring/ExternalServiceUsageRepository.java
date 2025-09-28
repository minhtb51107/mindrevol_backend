// src/main/java/com/example/demo/repository/monitoring/ExternalServiceUsageRepository.java
package com.example.demo.repository.monitoring;

import com.example.demo.model.monitoring.ExternalServiceUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExternalServiceUsageRepository extends JpaRepository<ExternalServiceUsage, Long> {
}