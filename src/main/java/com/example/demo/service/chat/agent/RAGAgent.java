package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RAGAgent implements Agent {

    private final PipelineManager pipelineManager;

    @Autowired
    public RAGAgent(PipelineManager pipelineManager) {
        this.pipelineManager = pipelineManager;
    }

    @Override
    public String getName() {
        return "RAGAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này để trả lời các câu hỏi dựa trên tài liệu và kiến thức chuyên sâu đã được cung cấp.";
    }

    @Override
    public RagContext execute(RagContext context) {
        // RAGAgent giờ đây chỉ cần gọi PipelineManager để chạy pipeline tương ứng
        // Tên pipeline có thể được truyền qua context để tăng tính linh hoạt
        String pipelineName = context.getPipelineName() != null ? context.getPipelineName() : "default-rag";
        return pipelineManager.run(context, pipelineName);
    }
}