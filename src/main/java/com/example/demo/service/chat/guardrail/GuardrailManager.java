package com.example.demo.service.chat.guardrail;

import com.example.demo.service.chat.guardrail.validators.PiiGuardrail;
import com.example.demo.service.chat.guardrail.validators.TopicGuardrail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GuardrailManager {

    private final List<Guardrail> inputGuardrails;
    private final List<Guardrail> outputGuardrails;

    // Tự động tiêm (inject) các bean Guardrail đã được định nghĩa
    @Autowired
    public GuardrailManager(PiiGuardrail piiGuardrail, TopicGuardrail topicGuardrail) {
        // Định nghĩa các quy tắc áp dụng cho đầu vào của người dùng
        this.inputGuardrails = List.of(piiGuardrail, topicGuardrail);
        
        // Định nghĩa các quy tắc áp dụng cho đầu ra của LLM
        // Ví dụ: Không cần kiểm tra PII ở đầu ra vì LLM không nên tạo ra nó
        this.outputGuardrails = List.of(topicGuardrail);
    }

    /**
     * Chạy pipeline kiểm tra cho đầu vào của người dùng.
     */
    public String checkInput(String userInput) {
        return executeChecks(userInput, inputGuardrails, "Input");
    }

    /**
     * Chạy pipeline kiểm tra cho đầu ra của LLM.
     */
    public String checkOutput(String llmResponse) {
        return executeChecks(llmResponse, outputGuardrails, "Output");
    }

    private String executeChecks(String content, List<Guardrail> guardrails, String type) {
        String sanitizedContent = content;
        for (Guardrail guardrail : guardrails) {
            try {
                sanitizedContent = guardrail.check(sanitizedContent);
            } catch (GuardrailViolationException e) {
                log.warn("{} guardrail violation detected by {}: {}", type, guardrail.getClass().getSimpleName(), e.getMessage());
                // Khi có vi phạm, trả về một thông báo an toàn và dừng pipeline
                return "Rất tiếc, tôi không thể xử lý yêu cầu này vì lý do an toàn và bảo mật.";
            }
        }
        return sanitizedContent;
    }
}