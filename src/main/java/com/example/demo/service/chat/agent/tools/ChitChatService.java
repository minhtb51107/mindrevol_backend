package com.example.demo.service.chat.agent.tools;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatSessionRepository; // ✅ THAY ĐỔI: Dùng trực tiếp Repository
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.result.GenerationStepResult;
import com.example.demo.service.chat.orchestration.steps.GenerationStep;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChitChatService {

    private final GenerationStep generationStep;
    private final ChatSessionRepository chatSessionRepository; // ✅ THAY ĐỔI: Inject Repository
    private final LangChainChatMemoryService chatMemoryService;

    /**
     * Tạo câu trả lời cho các cuộc trò chuyện thông thường (chitchat).
     *
     * @param query     Câu hỏi hoặc lời chào của người dùng.
     * @param sessionId ID của phiên trò chuyện.
     * @return Câu trả lời xã giao.
     */
    public String chitChat(String query, Long sessionId) {
        log.info("ChitChatService invoked for session {} with query: '{}'", sessionId, query);

        // 1. Lấy ChatSession và các đối tượng context cần thiết
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
        User user = session.getUser();
        ChatMemory chatMemory = chatMemoryService.getChatMemory(sessionId);

        // 2. ✅ SỬA LỖI: Tạo RagContext bằng Builder
        RagContext context = RagContext.builder()
                .session(session)
                .user(user)
                .chatMemory(chatMemory)
                .initialQuery(query)
                .query(query)
                .build();

        // 3. Gọi trực tiếp GenerationStep
        GenerationStepResult result = generationStep.execute(context);

        // 4. Trả về câu trả lời
        return result.getReply();
    }
}