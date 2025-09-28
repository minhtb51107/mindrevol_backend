package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;

// Sửa 1: Chuyển từ class thành interface
public interface QueryIntentClassificationService {

    // Sửa 2: Nhúng toàn bộ logic phân loại vào SystemMessage
    @SystemMessage(
        """
        You are a query classification expert. Your task is to analyze the user's query and classify it into one of the following predefined categories:
        - DYNAMIC_QUERY: For questions about real-time or very recent information like stock prices, financial news, today's weather, or the current time. Keywords: "giá cổ phiếu", "thị trường chứng khoán", "tin tức", "mới nhất", "thời tiết", "bây giờ là mấy giờ", "hôm nay", "hiện tại".
        - RAG_QUERY: For questions seeking specific information, facts, or data that would likely be found in internal documents or a knowledge base.
        - CHIT_CHAT: For casual conversation, greetings, pleasantries, or non-informational queries.
        - STATIC_QUERY: For general knowledge questions whose answers are stable and do not change frequently. This is the default if no other category fits.
        
        Your output MUST BE one of the enum values: RAG_QUERY, DYNAMIC_QUERY, CHIT_CHAT, STATIC_QUERY.
        """
    )
    QueryIntent classify(String query);
}