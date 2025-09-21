package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.result.GenerationStepResult;
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

    /**
     * Thực thi agent cho các cuộc trò chuyện thông thường (chitchat).
     * Agent này sẽ bỏ qua các bước phức tạp và đi thẳng đến bước tạo câu trả lời.
     */
    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing ChitChatAgent for query: '{}'", context.getInitialQuery());

        // 1. Gọi GenerationStep và nhận về đối tượng kết quả (GenerationStepResult)
        GenerationStepResult result = generationStep.execute(context);

        // 2. Cập nhật RagContext với dữ liệu từ kết quả trả về
        // Agent bây giờ chịu trách nhiệm cập nhật context, tương tự như PipelineManager
        context.setFinalLcMessages(result.getFinalLcMessages());
        context.setReply(result.getReply());

        // 3. Trả về context đã được cập nhật
        return context;
    }
}