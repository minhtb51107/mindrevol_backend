// Khai báo package và import các thư viện cần thiết
package com.example.demo.controller.chat;

import com.example.demo.dto.chat.ChatMessageDTO;
import com.example.demo.dto.chat.ChatRequest;
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

import java.util.List;
import java.util.Map;

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

    // Endpoint gửi một tin nhắn mới
    @PostMapping
    public ResponseEntity<?> sendMessage(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader, // Lấy token từ header
            @RequestBody Map<String, String> request) { // Lấy nội dung từ request body
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy nội dung tin nhắn từ request
        String content = request.get("content");
        // Kiểm tra nội dung tin nhắn không được trống
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Nội dung không được để trống");
        }

        // Tạo đối tượng ChatMessageDTO từ nội dung tin nhắn
        ChatMessageDTO input = new ChatMessageDTO();
        input.setRole("user"); // Vai trò là người dùng
        input.setContent(content); // Nội dung tin nhắn
        List<ChatMessageDTO> messages = List.of(input); // Tạo danh sách chỉ chứa tin nhắn này

        // Gửi tin nhắn đến AI service và nhận phản hồi
        String reply = chatAIService.processMessages(sessionId, messages, user);
        // Trả về phản hồi từ AI dưới dạng JSON
        return ResponseEntity.ok(Map.of("content", reply));
    }

    // Endpoint gửi một loạt tin nhắn (batch)
    @PostMapping("/batch")
    public ResponseEntity<String> chat(
            @PathVariable Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader, // Lấy token từ header
            @RequestBody ChatRequest request) { // Lấy danh sách tin nhắn từ request body
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy danh sách tin nhắn từ request
        List<ChatMessageDTO> messages = request.getMessages();
        // Gửi danh sách tin nhắn đến AI service và nhận phản hồi
        String reply = chatAIService.processMessages(sessionId, messages, user);
        // Trả về phản hồi từ AI dưới dạng chuỗi
        return ResponseEntity.ok(reply);
    }

    // Endpoint lấy tất cả tin nhắn của một session
    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable("sessionId") Long sessionId, // Lấy sessionId từ đường dẫn
            @RequestHeader("Authorization") String authHeader) { // Lấy token từ header
        
        // Xác thực và lấy thông tin user từ token
        User user = extractUserFromAuth(authHeader);
        // Lấy danh sách tin nhắn của session và trả về
        return ResponseEntity.ok(chatMessageService.getMessagesForSession(sessionId, user));
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
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }
}