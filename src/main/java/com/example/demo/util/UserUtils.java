package com.example.demo.util;

import com.example.demo.model.auth.User;
import com.example.demo.repository.auth.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public class UserUtils {

    /**
     * Lấy thông tin User entity hiện tại từ Spring Security Context.
     * @param userRepository Repository để truy vấn thông tin user.
     * @return User entity nếu tìm thấy, ngược lại trả về null.
     */
    public static User getCurrentUser(UserRepository userRepository) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null; // Không có user nào được xác thực
        }

        String username;
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }

        if (username == null) {
            return null;
        }

        // Dùng Optional để xử lý an toàn hơn
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.orElse(null);
    }
}