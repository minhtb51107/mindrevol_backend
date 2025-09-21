package com.example.demo.service.chat.guardrail.validators;

import com.example.demo.service.chat.guardrail.Guardrail;
import com.example.demo.service.chat.guardrail.GuardrailViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PiiGuardrail implements Guardrail {

    // Đọc mẫu regex từ file application.properties
    private final Pattern piiPattern;

    public PiiGuardrail(@Value("${guardrail.pii.pattern}") String piiRegex) {
        this.piiPattern = Pattern.compile(piiRegex);
    }

    @Override
    public String check(String content) throws GuardrailViolationException {
        if (content == null || content.isBlank()) {
            return content;
        }

        if (piiPattern.matcher(content).find()) {
            throw new GuardrailViolationException("Nội dung chứa thông tin cá nhân nhạy cảm (PII).");
        }
        return content;
    }
}