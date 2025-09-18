// src/main/java/com/example/demo/service/chat/fallback/FallbackService.java
package com.example.demo.service.chat.fallback;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class FallbackService {
    
    private static final List<String> GENERAL_FALLBACKS = List.of(
        "Xin lỗi, tôi chưa hiểu rõ câu hỏi của bạn. Bạn có thể diễn đạt lại được không?",
        "Tôi không chắc mình hiểu đúng ý bạn. Bạn có thể cho thêm ngữ cảnh được không?",
        "Hiện tại tôi gặp chút khó khăn với câu hỏi này. Bạn muốn hỏi về chủ đề cụ thể nào?",
        "Có vẻ như tôi cần thêm thông tin. Bạn có thể nói rõ hơn được không?"
    );
    
    private static final List<String> TECHNICAL_FALLBACKS = List.of(
        "Hiện tôi đang gặp sự cố kỹ thuật. Vui lòng thử lại sau ít phút.",
        "Hệ thống đang bận. Bạn có thể hỏi câu khác hoặc thử lại sau không?",
        "Kết nối của tôi đang không ổn định. Bạn vui lòng nhắc lại câu hỏi được không?"
    );
    
    private final Random random = new Random();
    
    public String getFallbackResponse(String originalQuery, String errorType) {
        if (isTechnicalError(errorType)) {
            return getRandomResponse(TECHNICAL_FALLBACKS);
        }
        
        // Analyze query for better fallback
        if (isComplexQuery(originalQuery)) {
            return "Câu hỏi của bạn khá phức tạp. Bạn có thể chia nhỏ thành nhiều câu hỏi không?";
        }
        
        if (isAmbiguousQuery(originalQuery)) {
            return "Tôi thấy có vài cách hiểu cho câu hỏi này. Bạn có thể cụ thể hơn được không?";
        }
        
        return getRandomResponse(GENERAL_FALLBACKS);
    }
    
    public String getEmergencyResponse() {
        return "Hiện tôi đang gặp sự cố. Vui lòng liên hệ quản trị viên hoặc thử lại sau. Xin cảm ơn!";
    }
    
    private boolean isTechnicalError(String errorType) {
        return errorType != null && 
              (errorType.contains("timeout") || errorType.contains("connection") || 
               errorType.contains("api") || errorType.contains("network"));
    }
    
    private boolean isComplexQuery(String query) {
        return query != null && 
              (query.length() > 100 || countWords(query) > 20 || 
               query.contains(" and ") || query.contains(" or "));
    }
    
    private boolean isAmbiguousQuery(String query) {
        return query != null && 
              (query.contains("?") || query.contains(" or ") || 
               query.toLowerCase().contains("what about"));
    }
    
    private int countWords(String text) {
        return text.split("\\s+").length;
    }
    
    private String getRandomResponse(List<String> responses) {
        return responses.get(random.nextInt(responses.size()));
    }
}