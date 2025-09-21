package com.example.demo.service.chat.guardrail;

/**
 * Interface chung cho mỗi "hàng rào" kiểm soát.
 * Mỗi implementation sẽ thực hiện một quy tắc kiểm tra cụ thể.
 */
public interface Guardrail {

    /**
     * Kiểm tra nội dung đầu vào.
     * @param content Nội dung cần kiểm tra (từ người dùng hoặc từ LLM).
     * @return Trả về chính nội dung đó nếu hợp lệ.
     * @throws GuardrailViolationException Ném ra exception nếu phát hiện vi phạm.
     */
    String check(String content) throws GuardrailViolationException;
}