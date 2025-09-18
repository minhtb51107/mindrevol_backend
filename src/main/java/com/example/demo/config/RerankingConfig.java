package com.example.demo.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RerankingConfig {

    @Value("${reranking.weights.semantic:0.5}")
    private double semanticWeight;
    
    @Value("${reranking.weights.recency:0.2}")
    private double recencyWeight;
    
    @Value("${reranking.weights.keyword:0.3}")
    private double keywordWeight;

    @Bean
    public Map<String, Double> rerankingWeights() {
        return Map.of(
            "semantic", semanticWeight,
            "recency", recencyWeight,
            "keyword", keywordWeight
        );
    }
}