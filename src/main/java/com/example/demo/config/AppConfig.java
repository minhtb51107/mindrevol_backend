package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import com.example.demo.repository.auth.UserRepository;
import com.example.demo.security.MyUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

	private final MyUserDetailsService myUserDetailsService;

    // ✅ BỔ SUNG: Bean này sẽ tạo ra AuthenticationProvider mà SecurityConfig đang cần.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // DaoAuthenticationProvider là một implementation phổ biến của AuthenticationProvider.
        // Nó lấy thông tin người dùng từ UserDetailsService và so sánh mật khẩu đã mã hóa.
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(myUserDetailsService); // Cung cấp dịch vụ tìm user
        authProvider.setPasswordEncoder(passwordEncoder());   // Cung cấp trình mã hóa mật khẩu
        return authProvider;
    }

    // ✅ BỔ SUNG: Bean này cần thiết cho endpoint đăng nhập.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ✅ BỔ SUNG: PasswordEncoder để mã hóa mật khẩu.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
	// Trong một class @Configuration nào đó, ví dụ AppConfig.java
	@Bean
	public RestTemplate restTemplate() {
	    return new RestTemplate();
	}
}