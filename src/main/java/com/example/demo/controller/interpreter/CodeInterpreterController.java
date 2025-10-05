package com.example.demo.controller.interpreter;

import com.example.demo.service.interpreter.CodeInterpreterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/interpreter")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Slf4j
public class CodeInterpreterController {

    private final CodeInterpreterService interpreterService;

    @PostMapping("/start")
    public ResponseEntity<?> startThread() {
        try {
            String threadId = interpreterService.createThread();
            return ResponseEntity.ok(Map.of("threadId", threadId));
        } catch (Exception e) {
            log.error("Could not start interpreter thread", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{threadId}/execute")
    public ResponseEntity<?> execute(
            @PathVariable String threadId,
            @RequestBody Map<String, String> payload) {

        String userInput = payload.get("input");
        if (userInput == null || userInput.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Input is required."));
        }

        try {
            String response = interpreterService.execute(threadId, userInput);
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            log.error("Error executing command in thread {}", threadId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
