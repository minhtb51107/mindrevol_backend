package com.example.demo.service.chat.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class OpenAIWebSearchTool {

    interface WebSearchAssistant {
        String search(String query);
    }

    private final WebSearchAssistant webSearchAssistant;

    public OpenAIWebSearchTool(ChatLanguageModel chatLanguageModel) {
        // Sử dụng chính ChatLanguageModel để tạo ra một "trợ lý" chuyên tìm kiếm
        // Mô hình sẽ tự quyết định cách tìm kiếm thông tin dựa trên câu hỏi
        this.webSearchAssistant = AiServices.create(WebSearchAssistant.class, chatLanguageModel);
    }

    @Tool("Performs a web search to find up-to-date information about a given topic.")
    public String searchWeb(String query) {
        return webSearchAssistant.search(query);
    }
}