//package com.example.demo.service.chat.agent;
//
//import com.example.demo.service.chat.orchestration.context.RagContext;
//
///**
// * Định nghĩa interface chung cho tất cả các Worker Agent.
// * Mỗi agent là một "chuyên gia" cho một loại nhiệm vụ cụ thể.
// */
//public interface Agent {
//
//    /**
//     * Trả về tên định danh duy nhất của agent.
//     * Orchestrator sẽ sử dụng tên này để gọi agent.
//     * @return Tên của agent (ví dụ: "RAGAgent").
//     */
//    String getName();
//
//    /**
//     * Cung cấp mô tả chi tiết về chức năng của agent.
//     * Orchestrator sẽ dựa vào mô tả này để quyết định khi nào nên sử dụng agent này.
//     * @return Mô tả chức năng.
//     */
//    String getDescription();
//
//    /**
//     * Thực thi nhiệm vụ chính của agent.
//     * @param context Đối tượng RagContext chứa toàn bộ trạng thái và dữ liệu cần thiết.
//     * @return RagContext sau khi đã được agent xử lý.
//     */
//    RagContext execute(RagContext context);
//}