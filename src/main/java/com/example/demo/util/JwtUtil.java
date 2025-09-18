package com.example.demo.util;

import com.example.demo.model.auth.User;
import com.example.demo.repository.auth.UserRepository;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final UserRepository userRepository; // ✅ Inject repo để lấy user

    private final String secret = "mysecretkeymysecretkeymysecretkey";
    private final long expirationMs = 7 * 24 * 60 * 60 * 1000; // 7 ngày

    private Key signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
//        System.out.println("🔐 SigningKey: " + Base64.getEncoder().encodeToString(signingKey.getEncoded()));
    }

    private Key getSigningKey() {
        return signingKey;
    }

    public String extractUsername(String token) {
        // 🔍🔥 THÊM LOG KIỂM TRA TOKEN TRƯỚC KHI PARSE
//        System.out.println("=== JwtUtil.extractUsername() DEBUG START ===");
//        System.out.println("Input token: '" + token + "'");
//        System.out.println("Token is null: " + (token == null));
        
        if (token != null) {
//            System.out.println("Token is blank: " + token.isBlank());
//            System.out.println("Token length: " + token.length());
            int dotCount = token.length() - token.replace(".", "").length();
//            System.out.println("Number of '.' characters: " + dotCount);
            
            // Kiểm tra nhanh cấu trúc JWT cơ bản
            if (dotCount != 2) {
//                System.err.println("❌ CẢNH BÁO: Token không có đúng 2 dấu chấm (.), có thể không phải JWT hợp lệ!");
            }
        } else {
//            System.err.println("❌ LỖI: Token truyền vào là null!");
        }

        try {
            // 🔍 Ghi lại thời điểm bắt đầu parse
//            System.out.println("Attempting to parse JWT...");
            
            String subject = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token) // 👈 Dòng gây lỗi MalformedJwtException
                .getBody()
                .getSubject();
                
//            System.out.println("✅ Parse thành công. Username/Email: " + subject);
//            System.out.println("=== JwtUtil.extractUsername() DEBUG END ===");
            return subject;
            
        } catch (MalformedJwtException e) {
//            System.err.println("❌ Lỗi MalformedJwtException: " + e.getMessage());
            e.printStackTrace(); // In full stacktrace để xem chi tiết
            throw e; // Ném lại exception để không che giấu lỗi
        } catch (Exception e) {
//            System.err.println("❌ Lỗi khác khi parse JWT: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public boolean validateToken(String token, org.springframework.security.core.userdetails.UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()));
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ✅ THÊM HÀM NÀY:
    public User getUserFromToken(String token) {
        String email = extractUsername(token);
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với email: " + email));
    }
}
