package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Áp dụng cho tất cả các đường dẫn
                .allowedOrigins("http://localhost:5173") // Thay bằng địa chỉ frontend của bạn
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Cho phép các method này
                .allowedHeaders("Authorization", "Content-Type", "X-Requested-With") // ✅ QUAN TRỌNG: Cho phép các header này
                .allowCredentials(true) // Cho phép gửi cookie hoặc thông tin xác thực
                .maxAge(3600); // Thời gian cache của pre-flight request
    }
}