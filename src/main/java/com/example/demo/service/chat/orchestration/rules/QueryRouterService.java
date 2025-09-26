// src/main/java/com/example/demo/service/chat/orchestration/rules/QueryRouterService.java
package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface QueryRouterService {

    @SystemMessage({
        "Your task is to classify the user's query into one of the following categories:",
        "RAG: For questions that require information from the internal knowledge base, documents, or need detailed explanation.",
        "TOOL: For questions about real-time information like weather, stocks, web search, or the current time.",
        // ✅ THÊM HẠNG MỤC MỚI VÀO ĐÂY
        "MEMORY_QUERY: For questions about the conversation history, such as 'what did I just say?', 'summarize our conversation', or 'what did we talk about earlier?'.",
        "CHITCHAT: For general conversation, greetings, or questions not covered by the other categories."
    })
    String route(@UserMessage String query);
}