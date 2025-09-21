package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.steps.GenerationStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChitChatAgent implements Agent {

    private final GenerationStep generationStep;

    @Override
    public String getName() {
        return "ChitChatAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này cho các cuộc trò chuyện thông thường, chào hỏi, tạm biệt, cảm ơn, hoặc các câu hỏi phiếm không có mục đích thông tin rõ ràng (ví dụ: 'Chào bạn', 'Bạn khỏe không?').";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing ChitChatAgent for query: '{}'", context.getInitialQuery());
        // For chitchat, we skip retrieval and reranking, and go straight to generation.
        return generationStep.execute(context);
    }
}