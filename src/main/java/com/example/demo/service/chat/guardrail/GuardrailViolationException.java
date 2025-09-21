package com.example.demo.service.chat.guardrail;

/**
 * Exception được ném ra khi một Guardrail phát hiện vi phạm.
 */
public class GuardrailViolationException extends RuntimeException {
    public GuardrailViolationException(String message) {
        super(message);
    }
}