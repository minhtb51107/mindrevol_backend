package com.example.demo.service.chat.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface RouterAgent {

	@SystemMessage({
	    "Bạn là một trợ lý AI thông minh và hữu ích, có khả năng sử dụng các công cụ để tìm kiếm thông tin.",
	    "Nhiệm vụ của bạn được chia làm hai bước:",
	    "1. Đầu tiên, phân tích câu hỏi của người dùng và chọn một công cụ (tool) phù hợp nhất để tìm thông tin trả lời.",
	    "2. Sau khi công cụ thực thi và trả về kết quả (ví dụ: thông tin thời tiết, kết quả tìm kiếm web), nhiệm vụ thứ hai của bạn là DỰA VÀO KẾT QUẢ ĐÓ để viết một câu trả lời cuối cùng, tự nhiên, và đầy đủ cho người dùng bằng tiếng Việt.",
	    "QUAN TRỌNG: Không bao giờ trả về kết quả thô của công cụ (như 'Title:', 'Snippet:') cho người dùng. Luôn luôn tổng hợp nó thành một câu trả lời hoàn chỉnh, mạch lạc."
	})
    String chat(@MemoryId Long sessionId, @UserMessage String userMessage);
}