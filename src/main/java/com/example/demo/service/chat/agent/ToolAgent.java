// src/main/java/com/example/demo/service/chat/agent/ToolAgent.java
package com.example.demo.service.chat.agent;

import dev.langchain4j.service.SystemMessage;

public interface ToolAgent {

    @SystemMessage({
        "You are a helpful assistant that has access to a set of tools.",
        "You can use these tools to answer questions about web search, weather, and the current time.",
        "Based on the user's question, you must select the appropriate tool and use it.",
        "After executing the tool, provide the most accurate and concise answer to the user based on the tool's output."
    })
    String chat(String userMessage);
}