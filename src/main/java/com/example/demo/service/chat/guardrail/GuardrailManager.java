package com.example.demo.service.chat.guardrail;

import io.micrometer.core.instrument.MeterRegistry; // <-- 1. Import MeterRegistry
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GuardrailManager {

    private final List<Guardrail> allGuardrails; // <-- Giữ một danh sách tất cả guardrail
    private final MeterRegistry meterRegistry;   // <-- 2. Thêm MeterRegistry làm dependency

    @Autowired
    public GuardrailManager(List<Guardrail> allGuardrails, MeterRegistry meterRegistry) {
        this.allGuardrails = allGuardrails;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Chạy pipeline kiểm tra cho đầu vào của người dùng.
     */
    public String checkInput(String userInput) {
        // Bạn có thể lọc các guardrail cần thiết cho đầu vào ở đây
        // Ví dụ: List<Guardrail> inputGuardrails = allGuardrails.stream()...
        return executeChecks(userInput, allGuardrails, "Input");
    }

    /**
     * Chạy pipeline kiểm tra cho đầu ra của LLM.
     */
    public String checkOutput(String llmResponse) {
        // Tương tự, lọc các guardrail cho đầu ra
        // Ví dụ, loại bỏ PiiGuardrail cho đầu ra
        List<Guardrail> outputGuardrails = allGuardrails.stream()
                .filter(g -> !(g instanceof com.example.demo.service.chat.guardrail.validators.PiiGuardrail))
                .collect(Collectors.toList());
        return executeChecks(llmResponse, outputGuardrails, "Output");
    }

    private String executeChecks(String content, List<Guardrail> guardrails, String type) {
        String sanitizedContent = content;
        for (Guardrail guardrail : guardrails) {
            try {
                sanitizedContent = guardrail.check(sanitizedContent);
            } catch (GuardrailViolationException e) {
                log.warn("{} guardrail violation detected by {}: {}", type, guardrail.getClass().getSimpleName(), e.getMessage());

                // --- BƯỚC 4: THÊM BỘ ĐẾM METRIC ---
                // Ghi nhận metric mỗi khi một guardrail phát hiện vi phạm.
                // Metric này sẽ có tên là "guardrail.violations" và có 2 tag (nhãn):
                // - "name": Tên của lớp Guardrail (ví dụ: "PiiGuardrail")
                // - "type": Loại kiểm tra ("Input" hoặc "Output")
                meterRegistry.counter("guardrail.violations",
                    "name", guardrail.getClass().getSimpleName(),
                    "type", type
                ).increment();
                // ------------------------------------

                // Khi có vi phạm, trả về một thông báo an toàn và dừng pipeline
                return "Rất tiếc, tôi không thể xử lý yêu cầu này vì lý do an toàn và bảo mật.";
            }
        }
        return sanitizedContent;
    }
}