package com.example.demo.service.chat.guardrail.validators;

import com.example.demo.service.chat.guardrail.Guardrail;
import com.example.demo.service.chat.guardrail.GuardrailViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicGuardrail implements Guardrail {

    // Đọc danh sách chủ đề cấm từ file application.properties
    @Value("${guardrail.topic.banned}")
    private List<String> bannedTopics;

    @Override
    public String check(String content) throws GuardrailViolationException {
        if (content == null || content.isBlank()) {
            return content;
        }
        
        String lowerCaseContent = content.toLowerCase();
        for (String topic : bannedTopics) {
            if (lowerCaseContent.contains(topic.toLowerCase())) {
                throw new GuardrailViolationException("Chủ đề '" + topic + "' không được phép thảo luận.");
            }
        }
        return content;
    }
}