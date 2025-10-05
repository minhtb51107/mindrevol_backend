package com.example.demo.service.chat.guardrail.validators;

import com.example.demo.service.chat.guardrail.Guardrail;
import com.example.demo.service.chat.guardrail.GuardrailViolationException;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAiModerationGuardrail implements Guardrail {

    private final ModerationModel moderationModel;

    /**
     * ✅ ĐÃ SỬA LỖI:
     * 1. Thay đổi signature của phương thức thành `String check(String content)` để khớp với interface Guardrail của bạn.
     * 2. Sử dụng `moderation.flagged()` thay vì `moderation.isFlagged()`.
     * 3. Trả về `content` nếu không có vi phạm, theo đúng yêu cầu của interface.
     */
    @Override
    public String check(String content) throws GuardrailViolationException {
        Moderation moderation = moderationModel.moderate(content).content();
        
        // Sử dụng phương thức chính xác từ class Moderation của LangChain4j
        if (moderation.flagged()) {
            // Nếu nội dung bị gắn cờ, ném ra một exception để ngăn chặn xử lý tiếp
            throw new GuardrailViolationException(
                "Nội dung của bạn vi phạm chính sách an toàn của chúng tôi. Yêu cầu đã bị chặn."
            );
        }
        
        // Trả về nội dung gốc nếu nó vượt qua kiểm duyệt
        return content;
    }
}