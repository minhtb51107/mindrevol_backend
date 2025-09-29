package com.example.demo.repository.chat;

import java.util.UUID;

// ✅ THAY ĐỔI Ở ĐÂY
// Interface này giờ đây sẽ lấy trực tiếp các cột cần thiết từ kết quả truy vấn.
public interface QuestionAnswerCacheProjection {
    // Tên phương thức phải khớp với tên cột (hoặc alias) trong native query
    UUID getId();
    String getAnswerText();
    double getDistance();
}