//package com.example.demo.controller.chat;
//
//// Model, DTO, Service của bạn
//import com.example.demo.dto.chat.ChatRequest;
//import com.example.demo.dto.chat.ChatMessageDTO; // Cần DTO này
//import com.example.demo.model.auth.User;
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.repository.auth.UserRepository;
//import com.example.demo.service.chat.ChatMessageService;
//import com.example.demo.service.chat.ChatSessionService;
//
//// Import của LangChain4j
//import dev.langchain4j.data.message.AiMessage;
//import dev.langchain4j.data.message.ChatMessage; // <-- Của LangChain4j
//import dev.langchain4j.data.message.UserMessage;
//import dev.langchain4j.model.StreamingResponseHandler;
//import dev.langchain4j.model.chat.StreamingChatLanguageModel;
//import dev.langchain4j.model.output.Response;
//
//import dev.langchain4j.data.message.SystemMessage;
//
//// Import của Spring và Java
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.core.task.TaskExecutor;
//import org.springframework.http.MediaType;
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
//
//import java.io.IOException;
//import java.security.Principal;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/chat/sessions")
//@RequiredArgsConstructor
//@Slf4j
//public class ChatStreamController {
//
//    private final StreamingChatLanguageModel streamingChatLanguageModel;
//    private final ChatMessageService chatMessageService;
//    private final ChatSessionService chatSessionService;
//    private final UserRepository userRepository;
//
//    @Autowired
//    @Qualifier("chatTaskExecutor") // <-- Đổi tên qualifier thành "chatTaskExecutor"
//    private TaskExecutor taskExecutor;
//
//    @PostMapping(value = "/{sessionId}/messages/stream", consumes = MediaType.APPLICATION_JSON_VALUE)
//    public SseEmitter streamMessage(
//            @PathVariable Long sessionId,
//            @RequestBody ChatRequest request, // <-- Chứa List<ChatMessageDTO>
//            Principal principal,
//            HttpServletResponse response) {
//
//        // 1. Lấy thông tin người dùng và xác thực
//        User user = userRepository.findByEmail(principal.getName())
//                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
//
//        // SỬA LỖI 2: Dùng getSessionIfAccessible
//        ChatSession session = chatSessionService.getSessionIfAccessible(sessionId, user)
//                .orElseThrow(() -> new AccessDeniedException("Không có quyền truy cập hoặc session không tồn tại"));
//
//        // 2. Cài đặt SseEmitter
//        setupSseResponseHeaders(response);
//        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
//
//        // 3. Lấy lịch sử tin nhắn TỪ REQUEST
//        // SỬA LỖI 1: Dùng request.getMessages()
//        List<ChatMessageDTO> messageDTOs = request.getMessages();
//        if (messageDTOs == null || messageDTOs.isEmpty()) {
//            emitter.completeWithError(new IllegalArgumentException("Request không chứa tin nhắn."));
//            return emitter;
//        }
//
//        // Chuyển đổi DTO sang định dạng của LangChain4j
//        List<ChatMessage> langChainMessages = messageDTOs.stream()
//                .map(this::convertDtoToLangChain)
//                .collect(Collectors.toList());
//
//        // 4. Lấy và Lưu tin nhắn mới của người dùng
//        ChatMessageDTO userMessageDTO = messageDTOs.get(messageDTOs.size() - 1);
//        if (!"user".equalsIgnoreCase(userMessageDTO.getRole())) {
//             emitter.completeWithError(new IllegalArgumentException("Tin nhắn cuối cùng trong danh sách phải từ 'user'."));
//             return emitter;
//        }
//        String userMessageContent = userMessageDTO.getContent();
//
//        // SỬA LỖI 3: Dùng saveMessage(ChatSession, String, String)
//        chatMessageService.saveMessage(session, "USER", userMessageContent);
//
//        // 5. Chạy streaming trong một thread riêng
//        taskExecutor.execute(() -> {
//            try {
//                StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<>() {
//                    
//                    private final StringBuilder completeMessage = new StringBuilder();
//
//                    @Override
//                    public void onNext(String token) {
//                        try {
//                            emitter.send(SseEmitter.event()
//                                    .data(Map.of("type", "token", "content", token))
//                                    .id(UUID.randomUUID().toString()));
//                            completeMessage.append(token);
//                        } catch (IOException e) {
//                            log.warn("Lỗi khi gửi SseEmitter token: {}", e.getMessage());
//                            emitter.completeWithError(e);
//                        }
//                    }
//
//                    @Override
//                    public void onComplete(Response<AiMessage> response) {
//                        try {
//                            String finalMessage = completeMessage.toString();
//                            log.info("Stream hoàn tất cho session {}.", sessionId);
//
//                            // SỬA LỖI 3: Dùng đúng phương thức saveMessage
//                            chatMessageService.saveMessage(session, "AI", finalMessage);
//
//                            emitter.send(SseEmitter.event()
//                                    .data(Map.of("type", "complete", "content", finalMessage))
//                                    .id(UUID.randomUUID().toString()));
//                        } catch (Exception e) {
//                            log.error("Lỗi khi lưu tin nhắn AI: {}", e.getMessage());
//                            emitter.completeWithError(e);
//                        } finally {
//                            emitter.complete();
//                        }
//                    }
//
//                    @Override
//                    public void onError(Throwable error) {
//                        try {
//                            log.error("Lỗi streaming từ AI: {}", error.getMessage());
//                            emitter.send(SseEmitter.event()
//                                    .data(Map.of("type", "error", "content", error.getMessage()))
//                                    .id(UUID.randomUUID().toString()));
//                        } catch (IOException e) {
//                            log.warn("Lỗi khi gửi SseEmitter error: {}", e.getMessage());
//                        } finally {
//                            emitter.completeWithError(error);
//                        }
//                    }
//                };
//
//                // Bắt đầu gọi streaming
//                streamingChatLanguageModel.generate(langChainMessages, handler);
//
//            } catch (Exception e) {
//                log.error("Lỗi nghiêm trọng khi bắt đầu stream: {}", e.getMessage(), e);
//                emitter.completeWithError(e);
//            }
//        });
//
//        // 6. Trả về emitter ngay lập tức
//        return emitter;
//    }
//
//    /**
//     * Helper: Cài đặt các header cần thiết cho Server-Sent Events.
//     */
//    private void setupSseResponseHeaders(HttpServletResponse response) {
//        response.setHeader("Cache-Control", "no-cache");
//        response.setHeader("Connection", "keep-alive");
//        response.setHeader("Content-Type", "text/event-stream");
//        response.setCharacterEncoding("UTF-8");
//    }
//
//    /**
//     * Helper: Chuyển đổi ChatMessageDTO của bạn sang interface ChatMessage của LangChain4j.
//     * (Giả định ChatMessageDTO có getRole() và getContent())
//     */
//    private ChatMessage convertDtoToLangChain(ChatMessageDTO dto) {
//        String role = dto.getRole(); // Giả định DTO có getRole()
//        String content = dto.getContent(); // Giả định DTO có getContent()
//
//        if ("USER".equalsIgnoreCase(role)) {
//            return UserMessage.from(content);
//        } else if ("AI".equalsIgnoreCase(role) || "ASSISTANT".equalsIgnoreCase(role)) {
//            return AiMessage.from(content);
//        } else if ("SYSTEM".equalsIgnoreCase(role)) {
//            return SystemMessage.from(content);
//        }
//        log.warn("Không rõ vai trò tin nhắn: {}", role);
//        return UserMessage.from(content); // Mặc định là user
//    }
//}