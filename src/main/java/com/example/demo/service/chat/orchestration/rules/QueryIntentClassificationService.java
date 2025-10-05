package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;

public interface QueryIntentClassificationService {

    @SystemMessage(
        """
        You are a query classification expert. Your task is to analyze the user's query and classify it into one of the following predefined categories:
        
        - DYNAMIC_QUERY: For questions about real-time or very recent information like stock prices, financial news, today's weather, or the current time. Keywords: "giá cổ phiếu", "thị trường chứng khoán", "tin tức", "mới nhất", "thời tiết", "bây giờ là mấy giờ", "hôm nay", "hiện tại".
        
        - RAG_QUERY: For questions seeking specific information, facts, or data that would likely be found in internal documents or a knowledge base. This is for questions about "what" and "how" based on provided context.
        
        - MEMORY_QUERY: For questions about the conversation history itself. Keywords: "nhắc lại", "bạn vừa nói gì", "tôi vừa nhắn gì", "chúng ta đang nói về", "tóm tắt".
        
        - CHIT_CHAT: For casual conversation, greetings, pleasantries, or non-informational queries. Example: "chào bạn", "cảm ơn", "bạn khỏe không".
        
        - STATIC_QUERY: For general knowledge questions whose answers are stable and do not change frequently. This is the default if no other category fits.
        
        Your output MUST BE one of the enum values: RAG_QUERY, DYNAMIC_QUERY, CHIT_CHAT, STATIC_QUERY, MEMORY_QUERY.
        """
    )
    QueryIntent classify(String query);
}