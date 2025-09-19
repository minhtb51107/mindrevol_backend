package com.example.demo.controller.chat; // (Hoặc một package controller mới nếu bạn muốn)

import com.example.demo.model.auth.User;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.document.DocumentIngestionService;
import com.example.demo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents") // Tạo một route API mới cho tài liệu
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    // Các dependency này là để sao chép logic xác thực user
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            User user = extractUserFromAuth(authHeader);
            
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "File không được để trống"));
            }

            // Gọi service ingestion
            ingestionService.ingestDocument(file, user);

            return ResponseEntity.ok(Map.of("message", "File " + file.getOriginalFilename() + " đã được nạp thành công."));
        
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Không thể nạp file: " + e.getMessage()));
        }
    }

    /**
     * Phương thức helper để xác thực và trích xuất thông tin user từ token
     * (Sao chép từ ChatSessionController. Bạn nên xem xét tái cấu trúc
     * phần này vào một class @Component riêng để tránh lặp code)
     */
    private User extractUserFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Thiếu token hoặc sai định dạng");
        }
        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}