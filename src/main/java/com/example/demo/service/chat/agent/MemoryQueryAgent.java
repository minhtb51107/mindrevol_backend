//package com.example.demo.service.chat.agent;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.service.chat.ChatMessageService;
//import com.example.demo.service.chat.orchestration.context.RagContext;
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class MemoryQueryAgent implements Agent {
//
//    private final ChatLanguageModel chatLanguageModel;
//    private final ChatMessageService chatMessageService;
//
//    @Override
//    public String getName() {
//        return "MemoryQueryAgent";
//    }
//
//    @Override
//    public String getDescription() {
//        return "Sử dụng agent này để trả lời các câu hỏi liên quan đến lịch sử cuộc trò chuyện.";
//    }
//
//    @Override
//    public RagContext execute(RagContext context) {
//        log.debug("Executing FINAL MemoryQueryAgent for query: '{}'", context.getInitialQuery());
//
//        List<ChatMessage> historyFromDb = chatMessageService.getMessagesForSession(
//                context.getSession().getId(),
//                context.getUser()
//        );
//
//        String currentUserQuery = context.getInitialQuery();
//        String prompt = buildFinalPrompt(historyFromDb, currentUserQuery);
//
//        String response = chatLanguageModel.generate(prompt);
//        context.setReply(response);
//        return context;
//    }
//
//    /**
//     * ✅ ĐÃ CẬP NHẬT PROMPT ĐỂ RÕ RÀNG VÀ CHÍNH XÁC HƠN
//     */
//    private String buildFinalPrompt(List<ChatMessage> dbHistory, String currentUserQuery) {
//        StringBuilder promptBuilder = new StringBuilder();
//        
//        promptBuilder.append("Bạn là một trợ lý AI cá tính, thông minh và có chút hài hước. Hãy nói chuyện với người dùng một cách tự nhiên, thân thiện như một người bạn. Đừng ngại sử dụng emojis để thể hiện cảm xúc.\n\n");
//        promptBuilder.append("--- BẮT ĐẦU LỊCH SỬ TRÒ CHUYỆN ---\n");
//
//        if (dbHistory.isEmpty()) {
//            promptBuilder.append("(Chưa có lịch sử nào được lưu)\n");
//        } else {
//            for (ChatMessage message : dbHistory) {
//                String role = "user".equalsIgnoreCase(message.getSender()) ? "Người dùng" : "Trợ lý AI";
//                promptBuilder.append(String.format("%s: %s\n", role, message.getContent()));
//            }
//        }
//
//        promptBuilder.append("--- KẾT THÚC LỊCH SỬ ---\n\n");
//
//        promptBuilder.append("HƯỚNG DẪN DÀNH CHO BẠN:\n");
//        promptBuilder.append("1. Nhiệm vụ của bạn là trả lời câu hỏi CUỐI CÙNG của người dùng: \"").append(currentUserQuery.trim()).append("\"\n");
//        
//        // ✅ HƯỚNG DẪN MỚI, CỰC KỲ QUAN TRỌNG
//        promptBuilder.append("2. QUAN TRỌNG: Để trả lời, hãy chỉ tập trung vào những tin nhắn GẦN NHẤT trong lịch sử. Nếu người dùng hỏi 'tôi vừa nhắn gì?', bạn PHẢI tìm tin nhắn 'Người dùng' cuối cùng TRƯỚC câu hỏi hiện tại, và trả lời dựa trên tin nhắn đó. Ví dụ: 'Bạn vừa hỏi 'bây giờ là mấy giờ?'.'\n");
//        
//        promptBuilder.append("3. Giữ vững phong cách cá tính, thông minh và thân thiện. Thêm emoji phù hợp vào cuối câu trả lời nhé!\n\n");
//        promptBuilder.append("Câu trả lời của bạn:");
//
//        return promptBuilder.toString();
//    }
//}
