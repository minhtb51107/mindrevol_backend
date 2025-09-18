// Khai báo package và import các thư viện cần thiết
package com.example.demo.controller.chat;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.chat.ChatAIService;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.ChatSessionService;
import com.example.demo.service.chat.TitleGeneratorService;
import com.example.demo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Đánh dấu đây là controller xử lý REST API
@RestController
// Base path cho tất cả endpoint trong controller này
@RequestMapping("/api/chat/sessions")
// Tự động tạo constructor với các tham số final
@RequiredArgsConstructor
// Cho phép truy cập từ domain frontend (React/Vue.js thường chạy ở port 5173)
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ChatSessionController {

    // Inject các dependency cần thiết thông qua constructor
    private final TitleGeneratorService chatAIService; // Service tạo tiêu đề tự động
    private final ChatSessionService chatSessionService; // Service quản lý phiên chat
    private final JwtUtil jwtUtil; // Utility xử lý JWT token
    private final UserRepository userRepository; // Repository truy cập dữ liệu user

    // Endpoint tạo phiên chat mới
    @PostMapping
    public ResponseEntity<ChatSession> createSession(
            @RequestHeader("Authorization") String authHeader, // Lấy token từ header
            @RequestBody Map<String, String> body) { // Lấy tiêu đề từ request body
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy tiêu đề từ request body (có thể null nếu không được cung cấp)
        String title = body.get("title");
        // Tạo phiên chat mới thông qua service
        ChatSession session = chatSessionService.createSession(user, title);
        // Trả về phiên chat vừa tạo
        return ResponseEntity.ok(session);
    }

    // Endpoint lấy danh sách tất cả phiên chat của user
    @GetMapping
    public ResponseEntity<List<ChatSession>> getSessions(
            @RequestHeader("Authorization") String authHeader) { // Lấy token từ header
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy danh sách phiên chat của user thông qua service
        return ResponseEntity.ok(chatSessionService.getSessionsForUser(user));
    }

    // Endpoint xóa một phiên chat
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> deleteSession(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader) { // Lấy token từ header
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Xóa phiên chat thông qua service (service sẽ kiểm tra quyền truy cập)
        chatSessionService.deleteSession(sessionId, user);
        // Trả về response không có nội dung (thành công)
        return ResponseEntity.noContent().build();
    }

    // Endpoint cập nhật tiêu đề phiên chat
    @PutMapping("/{sessionId}/title")
    public ResponseEntity<?> updateTitle(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader, // Lấy token từ header
            @RequestBody Map<String, String> request) { // Lấy tiêu đề mới từ request body
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy tiêu đề mới từ request
        String newTitle = request.get("title");
        // Kiểm tra tiêu đề không được trống
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Tiêu đề không được để trống");
        }
        
        // Cập nhật tiêu đề phiên chat thông qua service
        chatSessionService.updateSessionTitle(sessionId, newTitle, user);
        // Trả về thông báo thành công
        return ResponseEntity.ok("Đã đổi tên thành công");
    }

    // Endpoint tạo tiêu đề tự động bằng AI
    @PostMapping("/{sessionId}/generate-title")
    public ResponseEntity<String> generateSessionTitle(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader) throws java.nio.file.AccessDeniedException {
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Tạo tiêu đề tự động bằng AI dựa trên nội dung chat
        String generatedTitle = chatAIService.generateAITitle(sessionId, user);
        // Trả về tiêu đề đã được tạo
        return ResponseEntity.ok(generatedTitle);
    }

    // Endpoint lấy thông tin user hiện tại
    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUser(
            @RequestHeader("Authorization") String authHeader) { // Lấy token từ header
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Trả về thông tin user
        return ResponseEntity.ok(user);
    }

    // Phương thức helper để xác thực và trích xuất thông tin user từ token
    private User extractUserFromAuth(String authHeader) {
        // Kiểm tra header Authorization có hợp lệ không
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Thiếu token hoặc sai định dạng");
        }
        // Trích xuất token từ header (bỏ phần "Bearer ")
        String token = authHeader.substring(7);
        // Trích xuất email từ token
        String email = jwtUtil.extractUsername(token);
        // Tìm user trong database bằng email
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}