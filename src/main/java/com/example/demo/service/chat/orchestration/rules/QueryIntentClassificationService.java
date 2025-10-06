package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.service.SystemMessage;

public interface QueryIntentClassificationService {

	@SystemMessage("""
		    Bạn là một chuyên gia phân loại ý định của người dùng trong một cuộc trò chuyện.
		    Hãy phân loại câu hỏi cuối cùng của người dùng vào một trong các loại sau:

		    - CHIT_CHAT: Dành cho các câu chào hỏi, hỏi thăm, trò chuyện phiếm, tán gẫu không có mục đích rõ ràng.
		      Ví dụ: "chào bạn", "bạn khoẻ không?", "kể chuyện cười đi", "nói gì đi", "trò chuyện thôi".

		    - MEMORY_QUERY: Dành cho các câu hỏi trực tiếp về nội dung các tin nhắn đã nói trước đó trong cuộc trò chuyện.
		      Ví dụ: "bạn vừa nói gì?", "lúc nãy bạn có nhắc đến A, nó là gì vậy?", "tóm tắt lại cuộc nói chuyện xem".

		    - RAG_QUERY: Dành cho các câu hỏi cần tìm kiếm thông tin từ tài liệu, kiến thức được cung cấp.
		      Ví dụ: "hãy cho tôi biết về sản phẩm X", "thủ tục Y làm như thế nào?".

		    - STATIC_QUERY: Các câu hỏi tĩnh như "bạn là ai?", "bạn làm được gì?".

		    - DYNAMIC_QUERY: Các câu hỏi cần sử dụng công cụ bên ngoài như tìm kiếm web, lấy thông tin thời tiết, chứng khoán.
		      Ví dụ: "thời tiết hôm nay thế nào?", "giá cổ phiếu VNM".

		    Hãy chỉ trả về MỘT loại duy nhất.
		    """)
    QueryIntent classify(String query);
}