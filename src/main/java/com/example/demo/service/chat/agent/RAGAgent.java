package com.example.demo.service.chat.agent;

// ✅ THÊM CÁC IMPORT CẦN THIẾT
import com.example.demo.model.chat.PendingAction;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineManager;
import com.example.demo.service.chat.state.ConversationStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGAgent implements Agent {

    private final PipelineManager pipelineManager;
    private final ConversationStateService conversationStateService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "RAGAgent";
    }

    @Override
    public String getDescription() {
        return "Trả lời các câu hỏi dựa trên nội dung các tài liệu, file, văn bản đã được cung cấp và lưu trữ trong cơ sở kiến thức. KHÔNG dùng cho các câu hỏi chung chung hoặc cần thông tin thời gian thực.";
    }

    @Override
    public RagContext execute(RagContext context) {
        String pipelineName = context.getPipelineName() != null ? context.getPipelineName() : "default-rag";
        
        // 1. Chạy pipeline như bình thường
        RagContext resultContext = pipelineManager.run(context, pipelineName);

        // 2. Kiểm tra kết quả của pipeline
        // ✅ SỬA LỖI: Sử dụng .getReply() thay vì .getAnswer()
        if (resultContext.getReply() == null || resultContext.getReply().isEmpty()) {
            Long sessionId = context.getSession().getId();
            // ✅ SỬA LỖI: Sử dụng .getInitialQuery() thay vì .getQuery()
            String originalQuery = context.getInitialQuery();
            
            log.warn("Không tìm thấy kết quả nào từ RAG cho session {}. Kích hoạt fallback và chờ xác nhận.", sessionId);

            // ✅ THAY ĐỔI LỚN: THIẾT LẬP TRẠNG THÁI CHỜ
            try {
                // Tạo context để lưu lại câu hỏi gốc
                ObjectNode actionContext = objectMapper.createObjectNode();
                actionContext.put("original_query", originalQuery);
                String contextJson = objectMapper.writeValueAsString(actionContext);

                // Cập nhật trạng thái hội thoại
                conversationStateService.updatePendingAction(
                    sessionId,
                    PendingAction.AWAITING_INTERNET_SEARCH_CONFIRMATION,
                    contextJson
                );
                
                // Cập nhật câu trả lời trong context thành câu hỏi xác nhận
                // ✅ SỬA LỖI: Sử dụng .setReply()
                resultContext.setReply("Tôi không thể tìm thấy thông tin cụ thể trong tài liệu của mình. Bạn có muốn tôi tìm kiếm trên Internet không?");

            } catch (Exception e) {
                log.error("Lỗi khi thiết lập trạng thái chờ cho session {}", sessionId, e);
                // ✅ SỬA LỖI: Sử dụng .setReply()
                resultContext.setReply("Đã xảy ra lỗi, vui lòng thử lại.");
            }
        }
        
        // 3. Trả về context (đã được cập nhật nếu cần)
        return resultContext;
    }
}