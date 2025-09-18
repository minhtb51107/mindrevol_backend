//package com.example.demo.controller.chat;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//import com.example.demo.dto.ChatMessageDTO;
//import com.example.demo.dto.ChatRequest;
//import com.example.demo.model.ChatMessage;
//import com.example.demo.model.ChatSession;
//import com.example.demo.model.User;
//import com.example.demo.repository.UserRepository;
//import com.example.demo.service.ChatHistoryService;
//import com.example.demo.service.OpenAIService;
//import com.example.demo.util.JwtUtil;
//
//import lombok.RequiredArgsConstructor;
//
//@RestController
//@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
//@RequestMapping("/api/chat")
//@RequiredArgsConstructor
//public class ChatController {
//
//    private final JwtUtil jwtUtil;
//    private final UserRepository userRepository;
//    private final ChatHistoryService historyService;
//    private final OpenAIService openAIService;
//
//    // ✅ Gửi tin nhắn vào session (USER và AI)
//    @PostMapping("/sessions/{sessionId}/messages")
//    public ResponseEntity<?> sendMessageToSession(
//        @PathVariable("sessionId") Long sessionId,
//        @RequestHeader("Authorization") String authHeader,
//        @RequestBody Map<String, String> request) {
//
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            return ResponseEntity.status(401).body("Thiếu token");
//        }
//
//        String token = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//
//        String content = request.get("content");
//        if (content == null || content.trim().isEmpty()) {
//            return ResponseEntity.badRequest().body("Nội dung không được để trống");
//        }
//
//        // 👉 Tạo danh sách message để truyền vào hàm mới
//        ChatMessageDTO input = new ChatMessageDTO();
//        input.setRole("user");
//        input.setContent(content);
//        List<ChatMessageDTO> messages = List.of(input);
//
//        // 👉 Gọi hàm xử lý dùng ngữ cảnh + cosine similarity
//        String reply = historyService.processMessages(sessionId, messages, user);
//
//        // ✅ Lưu message của người dùng và AI (đã được xử lý bên trong processMessages)
//        // => Không cần lưu lại ở đây nữa
//
//        return ResponseEntity.ok(Map.of("content", reply));
//    }
//
//
//    // ✅ Lấy toàn bộ tin nhắn theo session
//    @GetMapping("/{sessionId}/messages")
//    public ResponseEntity<List<ChatMessage>> getMessages(
//    		@PathVariable("sessionId") Long sessionId,
//            @RequestHeader("Authorization") String authHeader) {
//
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            return ResponseEntity.status(401).build();
//        }
//
//        String token = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//
//        return ResponseEntity.ok(historyService.getMessages(sessionId, user));
//    }
//
//
//    // ✅ Lấy toàn bộ session của user hiện tại
//    @GetMapping("/sessions")
//    public ResponseEntity<List<ChatSession>> getAllSessionsForUser(
//            @RequestHeader("Authorization") String authHeader) {
//
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            return ResponseEntity.status(401).build();
//        }
//
//        String token = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//
//        return ResponseEntity.ok(historyService.getSessionsForUser(user));
//    }
//    
//    @PostMapping("/chat/sessions/{sessionId}/messages")
//    public ResponseEntity<String> chat(
//        @PathVariable Long sessionId,
//        @RequestHeader("Authorization") String authHeader,
//        @RequestBody ChatRequest request
//    ) {
//    	String token = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//        
//    	List<ChatMessageDTO> messages = request.getMessages();
//    	String reply = historyService.processMessages(sessionId, messages, user);
//
//        return ResponseEntity.ok(reply);
//    }
//    
//    @PostMapping("/sessions/{sessionId}/generate-title")
//    public ResponseEntity<String> generateSessionTitle(
//    	@PathVariable("sessionId") Long sessionId,
//        @RequestHeader("Authorization") String authHeader) {
//        
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            return ResponseEntity.status(401).body("Thiếu token");
//        }
//
//        String token = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//
//        String generatedTitle = historyService.generateAITitle(sessionId, user);
//        return ResponseEntity.ok(generatedTitle);
//    }
//    
//    @GetMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter streamChatResponse(
//            @PathVariable Long sessionId,
//            @RequestHeader("Authorization") String authHeader,
//            @RequestParam String token,
//            @RequestParam("content") String content) {
//
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            throw new AccessDeniedException("Thiếu token");
//        }
//
//        String token1 = authHeader.substring(7);
//        String email = jwtUtil.extractUsername(token1);
//        User user = userRepository.findByEmail(email)
//                .orElseThrow(() -> new AccessDeniedException("User not found"));
//
//        SseEmitter emitter = new SseEmitter();
//
//        new Thread(() -> {
//            try {
//                // ✅ Lấy messages ngữ cảnh
//                List<ChatMessage> recentMessages = historyService.getRecentMessages(sessionId, 50);
//                List<ChatMessage> context = new ArrayList<>(
//                        openAIService.getRelevantMessages(recentMessages, content, 6)
//                );
//
//                // ✅ Lưu tin nhắn user
//                historyService.saveMessage(sessionId, "user", content);
//
//                ChatMessage userMsg = ChatMessage.builder()
//                        .chatSession(ChatSession.builder().id(sessionId).build())
//                        .sender("user")
//                        .content(content)
//                        .timestamp(LocalDateTime.now())
//                        .build();
//                context.add(userMsg);
//
//                // ✅ Gọi OpenAI từng bước
//                openAIService.streamChatCompletion(context, tokenChunk -> {
//                    try {
//                        emitter.send(SseEmitter.event().data(tokenChunk));
//                    } catch (Exception e) {
//                        emitter.completeWithError(e);
//                    }
//                });
//
//                // ✅ Lưu toàn bộ tin nhắn assistant khi kết thúc stream
//                String fullReply = openAIService.getLastResponseContent();
//                historyService.saveMessage(sessionId, "assistant", fullReply);
//
//                emitter.complete();
//
//            } catch (Exception e) {
//                emitter.completeWithError(e);
//            }
//        }).start();
//
//        return emitter;
//    }
//
//}
//
