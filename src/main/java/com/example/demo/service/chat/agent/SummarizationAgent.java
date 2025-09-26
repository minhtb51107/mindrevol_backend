//// src/main/java/com/example/demo/service/chat/agent/SummarizationAgent.java
//package com.example.demo.service.chat.agent;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.service.chat.ChatMessageService;
//import com.example.demo.service.chat.orchestration.context.RagContext;
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import lombok.AllArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@AllArgsConstructor
//public class SummarizationAgent implements Agent {
//
//    private final ChatMessageService chatMessageService;
//    private final ChatLanguageModel chatLanguageModel;
//
//    @Override
//    public String getName() {
//        return "SummarizationAgent";
//    }
//
//    @Override
//    public String getDescription() {
//        return "Summarizes parts or all of the current conversation session based on user's request.";
//    }
//
//    @Override
//    public RagContext execute(RagContext context) {
//        log.debug("Executing Smart SummarizationAgent for session {}", context.getSession().getId());
//
//        // 1. Dùng AI để phân tích yêu cầu của người dùng
//        String userQuery = context.getInitialQuery();
//        int numberOfMessagesToFetch = determineScope(userQuery);
//
//        // 2. Lấy số lượng tin nhắn phù hợp
//        List<ChatMessage> messages;
//        if (numberOfMessagesToFetch == -1) { // -1 là tín hiệu để lấy tất cả
//            messages = chatMessageService.getMessagesForSession(context.getSession().getId(), context.getUser());
//        } else {
//            // Lấy N tin nhắn gần nhất (bao gồm cả tin nhắn yêu cầu tóm tắt)
//            messages = chatMessageService.getRecentMessages(context.getSession().getId(), numberOfMessagesToFetch + 1);
//        }
//
//        if (messages.isEmpty() || (messages.size() == 1 && numberOfMessagesToFetch > 0) ) {
//            context.setReply("Không có đủ nội dung để tóm tắt theo yêu cầu của bạn.");
//            return context;
//        }
//
//        // 3. Định dạng và tóm tắt
//        String conversationHistory = messages.stream()
//                .map(msg -> msg.getSender() + ": " + msg.getContent())
//                .collect(Collectors.joining("\n"));
//
//        String prompt = "Dựa vào cuộc trò chuyện sau, hãy tóm tắt lại nội dung chính:\n\n" + conversationHistory;
//
//        String summary = chatLanguageModel.generate(prompt);
//        context.setReply(summary);
//        
//        log.info("Successfully generated contextual summary for session {}", context.getSession().getId());
//        return context;
//    }
//
//    /**
//     * Dùng LLM để xác định xem người dùng muốn tóm tắt bao nhiêu tin nhắn.
//     * @param userQuery Câu hỏi của người dùng.
//     * @return Số lượng tin nhắn cần lấy, hoặc -1 nếu muốn lấy toàn bộ.
//     */
//    private int determineScope(String userQuery) {
//        String prompt = String.format(
//            "Phân tích yêu cầu của người dùng: \"%s\". Người dùng muốn tóm tắt bao nhiêu tin nhắn gần nhất? " +
//            "Nếu họ muốn tóm tắt 'phiên này' hoặc không nói rõ, trả về '-1'. " +
//            "Nếu họ muốn tóm tắt 'tin nhắn trước đó' hoặc 'vừa nhắn', trả về '1'. " +
//            "Nếu họ nói rõ một con số (ví dụ: '2 tin nhắn qua'), trả về con số đó. " +
//            "Chỉ trả lời bằng một con số duy nhất.",
//            userQuery
//        );
//
//        String response = chatLanguageModel.generate(prompt).trim();
//        try {
//            return Integer.parseInt(response);
//        } catch (NumberFormatException e) {
//            log.warn("Could not determine summarization scope for query: '{}'. Defaulting to full session.", userQuery);
//            return -1; // Mặc định là tóm tắt cả phiên nếu không phân tích được
//        }
//    }
//}