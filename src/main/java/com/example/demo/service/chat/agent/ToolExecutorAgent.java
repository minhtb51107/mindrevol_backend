package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("ToolAgent")
@RequiredArgsConstructor
public class ToolExecutorAgent implements Agent {

    private final ToolAgent intelligentToolAgent;

    @Override
    public String getName() {
        return "ToolAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng các công cụ chuyên dụng để trả lời câu hỏi về thông tin cần truy cập API bên ngoài, ví dụ: thời tiết, thời gian hiện tại, và thông tin tài chính, chứng khoán, cổ phiếu theo thời gian thực.";
    }

    // SỬA LỖI: Thay đổi kiểu trả về từ void sang RagContext
    @Override
    public RagContext execute(RagContext context) {
        String query = context.getQuery();
        log.debug("Executing intelligent ToolAgent for query: '{}'", query);

        String result = intelligentToolAgent.chat(context.getSession().getId(), query);

        context.setReply(result);
        
        // SỬA LỖI: Trả về context sau khi đã cập nhật
        return context;
    }
}