// src/main/java/com/example/demo/service/chat/orchestration/rules/QueryRouterService.java
package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

// ✅ Chuyển từ Class thành Interface
public interface QueryRouterService {

    @SystemMessage({
        "Your task is to classify the user's query into one of the following categories:",
        "RAG: For questions that require information from the internal knowledge base, documents, or need detailed explanation.",
        "TOOL: For questions about real-time information like weather, stocks, web search, or the current time.",
        "CHITCHAT: For general conversation, greetings, or questions not covered by the other categories."
    })
    String route(@UserMessage String query);
}