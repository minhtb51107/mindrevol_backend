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

    // ✅ ĐÃ THÊM MỚI
    private static final List<String> KNOWLEDGE_RETRIEVAL_FALLBACKS = List.of(
        "Xin lỗi, tôi không thể tìm thấy thông tin bạn yêu cầu vào lúc này. Vui lòng thử lại sau.",
        "Tôi đã gặp sự cố khi truy xuất dữ liệu cần thiết để trả lời bạn. Bạn có thể hỏi một câu khác không?",
        "Rất tiếc, có vẻ như nguồn kiến thức của tôi đang tạm thời không truy cập được."
    );

    // ✅ ĐÃ THÊM MỚI
    private static final List<String> GENERATION_FALLBACKS = List.of(
        "Xin lỗi, tôi đã gặp lỗi khi đang cố gắng tạo câu trả lời. Bạn có thể diễn đạt lại câu hỏi được không?",
        "Tôi gặp một chút khó khăn trong việc tổng hợp câu trả lời. Vui lòng thử lại với một câu hỏi khác.",
        "Có một lỗi nội bộ xảy ra khiến tôi không thể hoàn thành câu trả lời. Rất xin lỗi vì sự bất tiện này."
    );

    private final Random random = new Random();

    public String getFallbackResponse(String originalQuery, String errorType) {
        if (isTechnicalError(errorType)) {
            return getRandomResponse(TECHNICAL_FALLBACKS);
        }

        if (isComplexQuery(originalQuery)) {
            return "Câu hỏi của bạn khá phức tạp. Bạn có thể chia nhỏ thành nhiều câu hỏi không?";
        }

        if (isAmbiguousQuery(originalQuery)) {
            return "Tôi thấy có vài cách hiểu cho câu hỏi này. Bạn có thể cụ thể hơn được không?";
        }

        return getRandomResponse(GENERAL_FALLBACKS);
    }

    public String getEmergencyResponse() {
        return "Hiện tôi đang gặp sự cố nghiêm trọng. Vui lòng liên hệ quản trị viên hoặc thử lại sau. Xin cảm ơn!";
    }

    /**
     * ✅ Trả về thông báo lỗi khi không thể truy xuất kiến thức từ Vector Store.
     * @return Một chuỗi thông báo lỗi ngẫu nhiên.
     */
    public String getKnowledgeRetrievalErrorResponse() {
        return getRandomResponse(KNOWLEDGE_RETRIEVAL_FALLBACKS);
    }

    /**
     * ✅ Trả về thông báo lỗi khi mô hình ngôn ngữ không thể tạo ra câu trả lời.
     * @return Một chuỗi thông báo lỗi ngẫu nhiên.
     */
    public String getGenerationErrorResponse() {
        return getRandomResponse(GENERATION_FALLBACKS);
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
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private String getRandomResponse(List<String> responses) {
        return responses.get(random.nextInt(responses.size()));
    }
}