package com.example.demo.security;

import com.example.demo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
                                    throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            final String token = authHeader.substring(7);
            final String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                        );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // ⚠️ Đoạn này nếu đỏ → kiểm tra import và tên phương thức
        filterChain.doFilter(request, response);
    }
}

//package com.example.demo.security;
//
//import com.example.demo.util.JwtUtil;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.*;
//import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//
//@Component
//public class JwtAuthFilter extends OncePerRequestFilter {
//
//    @Autowired
//    private JwtUtil jwtUtil;
//
//    @Autowired
//    private UserDetailsService userDetailsService;
//
//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain filterChain)
//                                    throws ServletException, IOException {
//
//        // 🔍 THÊM LOG 1: In ra path và header
//        String requestPath = request.getServletPath();
//        String authHeader = request.getHeader("Authorization");
//        System.out.println("=== JwtAuthFilter DEBUG ===");
//        System.out.println("Request Path: " + requestPath);
//        System.out.println("Authorization Header: '" + authHeader + "'");
//
//        // 🔍 THÊM LOG 2: Kiểm tra nếu request đến public endpoint
//        // (Tạm thời ko sửa logic, chỉ để log kiểm tra)
//        if (requestPath.startsWith("/api/auth/")) {
//            System.out.println("⚠️  NHẬN DIỆN: Đây là public endpoint. Filter vẫn chạy (sẽ gây lỗi nếu header không hợp lệ).");
//        }
//
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            final String token = authHeader.substring(7); // Cắt lấy chữ "Bearer "
//            // 🔍 THÊM LOG 3: In ra token trước khi parse
//            System.out.println("Token extracted: '" + token + "'");
//            System.out.println("Token length: " + token.length());
//            // 🔍 THÊM LOG 4: Kiểm tra số dấu chấm trong token
//            int dotCount = token.length() - token.replace(".", "").length();
//            System.out.println("Number of '.' in token: " + dotCount);
//
//            final String username = jwtUtil.extractUsername(token); // 👈 Dòng 38, nơi xảy ra lỗi
//            // ... (phần còn lại của code) ...
//        } else {
//            System.out.println("ℹ️  Không tìm thấy Authorization header hoặc header không bắt đầu bằng 'Bearer '.");
//        }
//
//        System.out.println("=== END JwtAuthFilter DEBUG ===");
//
//        // ⚠️ Đoạn này nếu đỏ → kiểm tra import và tên phương thức
//        filterChain.doFilter(request, response);
//    }
//}
