package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.steps.MemoryQueryStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryQueryAgent implements Agent {

    private final MemoryQueryStep memoryQueryStep;

    @Override
    public String getName() {
        return "MemoryQueryAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này khi người dùng hỏi về chính cuộc trò chuyện hiện tại (ví dụ: 'tôi vừa nói gì?', 'bạn vừa nói gì?', 'nhắc lại lời tôi xem').";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing MemoryQueryAgent for query: '{}'", context.getInitialQuery());
        return memoryQueryStep.execute(context);
    }
}