package com.example.demo.controller.chat;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.chat.ChatAIService;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.integration.OpenAIService;
import com.example.demo.util.JwtUtil;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

//Tạo file ChatStreamController.java
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatStreamController {

 @Autowired
 private OpenAIService openAIService;

 @PostMapping("/{sessionId}/messages/stream")
 public SseEmitter streamMessage(
         @PathVariable String sessionId,
         @RequestBody Map<String, String> requestBody,
         HttpServletResponse response) {
     
     response.setHeader("Cache-Control", "no-cache");
     response.setHeader("Connection", "keep-alive");
     response.setContentType("text/event-stream");
     response.setCharacterEncoding("UTF-8");

     SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
     
     String content = requestBody.get("content");
     String userId = "user-" + sessionId; // Hoặc lấy từ authentication

     // Giả sử bạn có service để lấy lịch sử tin nhắn
     List<Map<String, String>> messages = getMessageHistory(sessionId);
     messages.add(Map.of("role", "user", "content", content));

     openAIService.getChatCompletionStream(
         messages,
         "gpt-3.5-turbo",
         500,
         userId,
         token -> {
             try {
                 emitter.send(SseEmitter.event()
                     .data(Map.of("type", "token", "content", token))
                     .id(UUID.randomUUID().toString()));
             } catch (IOException e) {
                 emitter.completeWithError(e);
             }
         },
         complete -> {
             try {
				emitter.send(SseEmitter.event()
				     .data(Map.of("type", "complete", "content", complete))
				     .id(UUID.randomUUID().toString()));
			 } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			 }
             emitter.complete();
         },
         error -> {
             try {
				emitter.send(SseEmitter.event()
				     .data(Map.of("type", "error", "content", error.getMessage()))
				     .id(UUID.randomUUID().toString()));
			 } catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			 }
             emitter.completeWithError(error);
         }
     );

     return emitter;
 }

 private List<Map<String, String>> getMessageHistory(String sessionId) {
     // Triển khai logic lấy lịch sử tin nhắn từ database
     return new ArrayList<>();
 }
}