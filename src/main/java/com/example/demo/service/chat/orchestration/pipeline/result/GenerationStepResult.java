package com.example.demo.service.chat.orchestration.pipeline.result;

import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class GenerationStepResult {
    String ragContextString;
    Map<String, Object> userPreferences;
    List<ChatMessage> finalLcMessages;
    String reply;
}