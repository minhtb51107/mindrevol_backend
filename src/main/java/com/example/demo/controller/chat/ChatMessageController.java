package com.example.demo.controller.chat;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.chat.ChatAIService;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // <-- Thêm import cho logging
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; // <-- Import SseEmitter

import java.util.List;

@RestController
@RequestMapping("/api/chat/sessions/{sessionId}/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Slf4j // <-- Thêm annotation Slf4j
public class ChatMessageController {

    private final ChatAIService chatAIService;
    private final ChatMessageService chatMessageService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // --- ENDPOINT MỚI CHO STREAMING ---
    /**
     * Xử lý yêu cầu chat và trả về phản hồi dưới dạng luồng (stream).
     * Client sẽ kết nối tới endpoint này để nhận các token ngay khi AI tạo ra.
     */
    @PostMapping("/stream")
    public SseEmitter streamMessage(
            @PathVariable("sessionId") Long sessionId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("content") String content,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        User user = extractUserFromAuth(authHeader);
        log.info("SSE stream requested for session {} by user {}", sessionId, user.getEmail());

        // 1. Tạo SseEmitter với thời gian timeout (ví dụ: 10 phút)
        SseEmitter emitter = new SseEmitter(600_000L);

        // 2. Định nghĩa các callback để xử lý vòng đời của emitter
        emitter.onCompletion(() -> log.info("SseEmitter is completed for session {}", sessionId));
        emitter.onTimeout(() -> log.warn("SseEmitter is timed out for session {}", sessionId));
        emitter.onError(e -> log.error("SseEmitter got an error for session {}", sessionId, e));

        // 3. Gọi service để bắt đầu xử lý bất đồng bộ
        // Truyền emitter vào service để nó có thể gửi dữ liệu về client
        chatAIService.processStreamMessages(
                sessionId,
                content,
                (file != null && file.isEmpty()) ? null : file,
                user,
                emitter
        );

        // 4. Trả về emitter ngay lập tức cho client.
        // Spring sẽ giữ kết nối này mở để server có thể gửi sự kiện (event)
        return emitter;
    }


    // --- Endpoint đồng bộ (cũ) của bạn ---
    /**
     * Xử lý yêu cầu chat và trả về toàn bộ phản hồi sau khi AI xử lý xong.
     */
    @PostMapping
    public ResponseEntity<String> sendMessage(
            @PathVariable("sessionId") Long sessionId,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("content") String content,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        User user = extractUserFromAuth(authHeader);

        if (file != null && file.isEmpty()) {
            file = null;
        }

        String aiResponse = chatAIService.processMessages(sessionId, content, file, user);
        return ResponseEntity.ok(aiResponse);
    }


    // --- Endpoint lấy lịch sử tin nhắn (Giữ nguyên) ---
    @GetMapping
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable("sessionId") Long sessionId,
            @RequestHeader("Authorization") String authHeader) {

        User user = extractUserFromAuth(authHeader);
        return ResponseEntity.ok(chatMessageService.getMessagesForSession(sessionId, user));
    }

    // --- Phương thức helper (Giữ nguyên) ---
    private User extractUserFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Thiếu token hoặc sai định dạng");
        }
        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
    }
}