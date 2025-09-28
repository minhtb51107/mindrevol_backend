// src/main/java/com/example/demo/service/CostTrackingService.java
package com.example.demo.service;

import com.example.demo.model.auth.User;
import com.example.demo.model.monitoring.ExternalServiceUsage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.monitoring.ExternalServiceUsageRepository;
import com.example.demo.util.UserUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CostTrackingService {

    private final ExternalServiceUsageRepository repository;
    private final UserRepository userRepository;

    public void recordUsage(String serviceName, String usageUnit, Long usageAmount, BigDecimal cost) {
        User currentUser = UserUtils.getCurrentUser(userRepository);
        if (currentUser == null) {
            // Or assign to a default system user
            return;
        }

        ExternalServiceUsage usage = new ExternalServiceUsage();
        usage.setServiceName(serviceName);
        usage.setUsageUnit(usageUnit);
        usage.setUsageAmount(usageAmount);
        usage.setCost(cost);
        usage.setUser(currentUser);
        usage.setCreatedAt(LocalDateTime.now());
        
        repository.save(usage);
    }
}