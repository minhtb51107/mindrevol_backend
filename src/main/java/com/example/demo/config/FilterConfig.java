package com.example.demo.config;

import com.example.demo.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    /**
     * Bean này ngăn không cho JwtAuthFilter được tự động đăng ký với servlet container.
     * Bằng cách đặt setEnabled(false), chúng ta có thể kiểm soát chính xác vị trí của nó
     * trong chuỗi filter của Spring Security mà không lo nó bị áp dụng hai lần hoặc sai thời điểm.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // ✅ Rất quan trọng: Vô hiệu hóa việc đăng ký toàn cục
        return registration;
    }
}