// Khai báo package và import các thư viện cần thiết
package com.example.demo.controller.chat;

import com.example.demo.dto.chat.ChatMessageDTO;
// import com.example.demo.dto.chat.ChatRequest; // ✅ ĐÃ XÓA
import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.chat.ChatAIService;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // ✅ THÊM MỚI

import java.util.List;
// import java.util.Map; // ✅ ĐÃ XÓA

// Đánh dấu đây là controller xử lý REST API
@RestController
// Base path cho tất cả endpoint trong controller này, với {sessionId} là path variable
@RequestMapping("/api/chat/sessions/{sessionId}/messages")
// Tự động tạo constructor với các tham số final
@RequiredArgsConstructor
// Cho phép truy cập từ domain frontend (React/Vue.js thường chạy ở port 5173)
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ChatMessageController {

    // Inject các dependency cần thiết thông qua constructor
    private final ChatAIService chatAIService; // Service xử lý AI chat
    private final ChatMessageService chatMessageService; // Service quản lý tin nhắn
    private final JwtUtil jwtUtil; // Utility xử lý JWT token
    private final UserRepository userRepository; // Repository truy cập dữ liệu user

    // ✅ THAY ĐỔI HOÀN TOÀN ENDPOINT 'sendMessage'
    @PostMapping
    public ResponseEntity<String> sendMessage(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader, // Lấy token từ header
            // Sử dụng @RequestParam thay vì @RequestBody
            @RequestParam("content") String content,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        
        // Kiểm tra xem file có hợp lệ không (ví dụ: không rỗng nhưng không có nội dung)
        if (file != null && file.isEmpty()) {
            file = null;
        }

        // Gọi service chính với (text) và (file)
        String aiResponse = chatAIService.processMessages(sessionId, content, file, user);
        
        return ResponseEntity.ok(aiResponse);
    }

    // ✅ Endpoint /batch ĐÃ BỊ XÓA
    // @PostMapping("/batch")
    // public ResponseEntity<String> chat(...) { ... }

    // Endpoint lấy tất cả tin nhắn của một session (Giữ nguyên)
    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader) { // Lấy token từ header
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy danh sách tin nhắn của session và trả về
        return ResponseEntity.ok(chatMessageService.getMessagesForSession(sessionId, user));
    }

    // Phương thức helper để xác thực và trích xuất thông tin user từ token (Giữ nguyên)
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
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }
}