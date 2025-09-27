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
        // ✅ MÔ TẢ MỚI: Rõ ràng hơn, chỉ trả lời dựa trên tài liệu đã cung cấp.
        return "Trả lời các câu hỏi dựa trên nội dung các tài liệu, file, văn bản đã được cung cấp và lưu trữ trong cơ sở kiến thức. KHÔNG dùng cho các câu hỏi chung chung hoặc cần thông tin thời gian thực.";
    }

    @Override
    public RagContext execute(RagContext context) {
        // RAGAgent giờ đây chỉ cần gọi PipelineManager để chạy pipeline tương ứng
        // Tên pipeline có thể được truyền qua context để tăng tính linh hoạt
        String pipelineName = context.getPipelineName() != null ? context.getPipelineName() : "default-rag";
        return pipelineManager.run(context, pipelineName);
    }
}