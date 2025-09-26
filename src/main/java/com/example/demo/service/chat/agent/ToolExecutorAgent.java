//package com.example.demo.service.chat.agent;
//
//import com.example.demo.service.chat.orchestration.context.RagContext;
//import dev.langchain4j.memory.ChatMemory;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
///**
// * Đây là class wrapper, implement interface Agent chung của hệ thống.
// * Nó sử dụng ToolAgent (do LangChain4j tạo ra) để thực thi các tác vụ liên quan đến tool.
// */
//@Slf4j
//@Service("ToolAgent") // Đặt tên là "ToolAgent" để Orchestrator có thể tìm thấy
//@RequiredArgsConstructor
//public class ToolExecutorAgent implements Agent {
//
//    // Dependency sẽ là interface ToolAgent mà bạn đã định nghĩa
//    private final ToolAgent langchainToolAgent;
//
//    @Override
//    public String getName() {
//        return "ToolAgent";
//    }
//
//    @Override
//    public String getDescription() {
//        return "Sử dụng khi cần dùng các công cụ bên ngoài như tra cứu thời tiết, giá cổ phiếu, hoặc tìm kiếm trên web.";
//    }
//
//    @Override
//    public RagContext execute(RagContext context) {
//        log.debug("Executing ToolExecutorAgent for query: '{}'", context.getInitialQuery());
//
//        // Lấy lịch sử hội thoại từ context
//        ChatMemory chatMemory = context.getChatMemory();
//
//        // Thêm câu hỏi hiện tại của người dùng vào bộ nhớ
//        // (Lưu ý: ToolAgent của bạn cần được cập nhật để nhận cả history)
//        // String response = langchainToolAgent.chat(context.getInitialQuery()); // Nếu ToolAgent chỉ nhận userMessage
//
//        // ---- ĐỀ XUẤT CẬP NHẬT ToolAgent.java để nhận cả history ----
//        // Xem bước 2 để cập nhật interface
//        String response = langchainToolAgent.chat(chatMemory, context.getInitialQuery());
//
//        // Cập nhật câu trả lời vào context
//        context.setReply(response);
//
//        // Trả về context đã được cập nhật
//        return context;
//    }
//}