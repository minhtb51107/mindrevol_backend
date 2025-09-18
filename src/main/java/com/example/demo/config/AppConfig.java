//package com.example.demo.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class AppConfig {
//
//    @Value("${app.google.client-id}")
//    private String googleClientId;
//
//    @Value("${app.verification.code-expiration-minutes}")
//    private int codeExpirationMinutes;
//
//    @Value("${app.verification.max-attempts-per-hour}")
//    private int maxAttemptsPerHour;
//
//    public String getGoogleClientId() {
//        return googleClientId;
//    }
//
//    public int getCodeExpirationMinutes() {
//        return codeExpirationMinutes;
//    }
//
//    public int getMaxAttemptsPerHour() {
//        return maxAttemptsPerHour;
//    }
//}