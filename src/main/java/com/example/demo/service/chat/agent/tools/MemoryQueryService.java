package com.example.demo.service.chat.agent.tools;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.steps.MemoryQueryStep; // Sử dụng đúng dependency
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoryQueryService {

    private final MemoryQueryStep memoryQueryStep;
    private final ChatSessionRepository chatSessionRepository;
    private final LangChainChatMemoryService chatMemoryService;

    /**
     * Trả lời câu hỏi dựa trên lịch sử trò chuyện ngắn hạn.
     *
     * @param query     Câu hỏi của người dùng.
     * @param sessionId ID của phiên trò chuyện.
     * @return Câu trả lời từ bộ nhớ.
     */
    public String answerFromHistory(String query, Long sessionId) {
        log.info("MemoryQueryService invoked for session {} with query: '{}'", sessionId, query);

        // 1. Lấy ChatSession và các đối tượng context cần thiết
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
        User user = session.getUser();
        ChatMemory chatMemory = chatMemoryService.getChatMemory(sessionId);

        // 2. Tạo RagContext bằng Builder
        RagContext context = RagContext.builder()
                .session(session)
                .user(user)
                .chatMemory(chatMemory)
                .initialQuery(query)
                .query(query)
                .build();

        // 3. ✅ SỬA LỖI: Gọi MemoryQueryStep và nhận về RagContext
        RagContext resultContext = memoryQueryStep.execute(context);

        // 4. ✅ SỬA LỖI: Trả về câu trả lời từ context đã được cập nhật
        return resultContext.getReply();
    }
}