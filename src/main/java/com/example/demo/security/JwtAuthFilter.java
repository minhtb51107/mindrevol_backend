package com.example.demo.security;

import com.example.demo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    // ✅ BƯỚC 1: Xác định danh sách các đường dẫn công khai không cần kiểm tra JWT.
    // Bạn có thể thêm các đường dẫn khác vào đây, ví dụ: "/v3/api-docs/**", "/swagger-ui/**"
    private final List<String> publicPaths = Arrays.asList("/api/auth");

    // ✅ BƯỚC 2: Ghi đè phương thức shouldNotFilter.
    // Phương thức này sẽ quyết định xem filter có nên được thực thi hay không.
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Trả về true (bỏ qua filter) nếu đường dẫn của request bắt đầu bằng một trong các publicPaths.
        return publicPaths.stream().anyMatch(p -> path.startsWith(p));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
                                    throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Chỉ xử lý nếu header tồn tại, bắt đầu bằng "Bearer " và có nội dung token.
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
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

        // Chuyển tiếp request và response cho filter tiếp theo trong chuỗi.
        filterChain.doFilter(request, response);
    }
}