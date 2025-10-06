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
    // Xóa các dependency không cần thiết nếu có, vì giờ chúng ta lấy từ context
    // private final ChatSessionRepository chatSessionRepository; 
    // private final LangChainChatMemoryService chatMemoryService;

    /**
     * Sửa đổi phương thức để nhận vào RagContext
     */
    public String chitChat(RagContext context) { // <-- THAY ĐỔI Ở ĐÂY
        log.info("ChitChatService invoked for session {} with query: '{}'", 
                 context.getSession().getId(), context.getQuery());

        // ✅ BƯỚC 1: XÓA TOÀN BỘ PHẦN TẠO LẠI CONTEXT
        /* ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
        User user = session.getUser();
        ChatMemory chatMemory = chatMemoryService.getChatMemory(sessionId);

        RagContext context = RagContext.builder()
                .session(session)
                .user(user)
                .chatMemory(chatMemory)
                .initialQuery(query)
                .query(query)
                .build();
        */

        // ✅ BƯỚC 2: GỌI TRỰC TIẾP GENERATION STEP VỚI CONTEXT ĐÃ CÓ
        GenerationStepResult result = generationStep.execute(context);

        // Trả về câu trả lời
        return result.getReply();
    }
}