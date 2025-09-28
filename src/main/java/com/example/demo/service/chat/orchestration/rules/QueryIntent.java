package com.example.demo.service.chat.orchestration.rules;

public enum QueryIntent {
    /**
     * Câu hỏi cần dữ liệu thời gian thực (giá cổ phiếu, thời tiết, tin tức mới nhất).
     * Sẽ bỏ qua cache và gọi Tool.
     */
    DYNAMIC_QUERY,

    /**
     * Câu hỏi về kiến thức tĩnh, không hoặc rất hiếm khi thay đổi (Thủ đô của Pháp là gì?).
     * Ưu tiên hàng đầu cho việc sử dụng và lưu cache.
     */
    STATIC_QUERY,

    /**
     * Câu hỏi cần tra cứu thông tin từ tài liệu nội bộ đã được nạp.
     */
    RAG_QUERY,

    /**
     * Trò chuyện thông thường, không mang tính truy vấn thông tin.
     */
    CHIT_CHAT
}