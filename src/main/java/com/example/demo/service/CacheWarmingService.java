package com.example.demo.service;

import com.example.demo.service.chat.ChatAIService; // Hoặc service chính xử lý chat của bạn
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheWarmingService {

    private final ChatAIService chatAIService; // Service chính xử lý request chat
    private final ResourceLoader resourceLoader;

    /**
     * Tự động chạy khi ứng dụng khởi động xong.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCacheOnStartup() {
        log.info("Application ready. Starting cache warming process...");
        List<String> commonQuestions = loadCommonQuestions();

        if (commonQuestions.isEmpty()) {
            log.warn("Cache warming question file is empty or not found. Skipping.");
            return;
        }

        for (String question : commonQuestions) {
            try {
                // Giả lập một request chat để kích hoạt pipeline và lưu vào cache
                // Bạn cần điều chỉnh cho phù hợp với logic của ChatAIService
                // Ví dụ: chatAIService.chat(sessionId, question);
                // Ở đây chúng ta chỉ log ra để minh họa
                log.info("Warming cache for question: \"{}\"", question);
                // Dòng dưới đây là quan trọng nhất, nó sẽ thực thi và lưu cache
                // chatAIService.processUserMessage(someDefaultUser, someDefaultSession, question);
            } catch (Exception e) {
                log.error("Error warming cache for question: '{}'", question, e);
            }
        }
        log.info("Cache warming process finished.");
    }

    private List<String> loadCommonQuestions() {
        List<String> questions = new ArrayList<>();
        Resource resource = resourceLoader.getResource("classpath:cache/common_questions.txt");
        if (!resource.exists()) {
            return questions;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    questions.add(line.trim());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load common questions for cache warming.", e);
        }
        return questions;
    }
}