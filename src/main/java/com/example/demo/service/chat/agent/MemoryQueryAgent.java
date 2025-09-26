// src/main/java/com/example/demo/service/chat/agent/MemoryQueryAgent.java
package com.example.demo.service.chat.agent;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.service.chat.ChatMessageService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryQueryAgent implements Agent {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatMessageService chatMessageService;

    @Override
    public String getName() {
        return "MemoryQueryAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này để trả lời các câu hỏi liên quan đến lịch sử cuộc trò chuyện.";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing FINAL MemoryQueryAgent for query: '{}'", context.getInitialQuery());

        // 1. Lấy lịch sử đã được lưu trong DB (tính đến trước lượt này)
        List<ChatMessage> historyFromDb = chatMessageService.getMessagesForSession(
                context.getSession().getId(),
                context.getUser()
        );

        // 2. Lấy câu hỏi hiện tại của người dùng từ context
        String currentUserQuery = context.getInitialQuery();

        // 3. Xây dựng prompt kết hợp cả hai nguồn
        String prompt = buildFinalPrompt(historyFromDb, currentUserQuery);

        // 4. Gọi LLM và trả về kết quả
        String response = chatLanguageModel.generate(prompt);
        context.setReply(response);
        return context;
    }

    private String buildFinalPrompt(List<ChatMessage> dbHistory, String currentUserQuery) {
        StringBuilder promptBuilder = new StringBuilder();
        
        // 1. Định hình Persona ngay từ đầu
        promptBuilder.append("Bạn là một trợ lý AI cá tính, thông minh và có chút hài hước. Hãy nói chuyện với người dùng một cách tự nhiên, thân thiện như một người bạn (có thể xưng hô 'tôi' và gọi người dùng là 'bạn' hoặc 'ông' nếu phù hợp). Đừng ngại sử dụng emojis để thể hiện cảm xúc. TUYỆT ĐỐI không trả lời một cách máy móc.\n\n");

        promptBuilder.append("--- BẮT ĐẦU LỊCH SỬ TRÒ CHUYỆN ---\n");

        if (dbHistory.isEmpty()) {
            promptBuilder.append("(Chưa có lịch sử nào được lưu)\n");
        } else {
            for (ChatMessage message : dbHistory) {
                String role = "user".equalsIgnoreCase(message.getSender()) ? "Người dùng" : "Trợ lý AI";
                promptBuilder.append(String.format("%s: %s\n", role, message.getContent()));
            }
        }

        promptBuilder.append("--- KẾT THÚC LỊCH SỬ ---\n\n");

        // 2. Hướng dẫn chi tiết hơn về phong cách trả lời
        promptBuilder.append("HƯỚNG DẪN ĐẶC BIỆT DÀNH CHO BẠN:\n");
        promptBuilder.append("1. Nhiệm vụ của bạn là trả lời câu hỏi CUỐI CÙNG của người dùng: \"").append(currentUserQuery.trim()).append("\"\n");
        promptBuilder.append("2. Hãy trả lời dựa trên LỊCH SỬ TRÒ CHUYỆN ở trên.\n");
        promptBuilder.append("3. Nếu người dùng hỏi 'tôi vừa nhắn gì?', hãy nhìn vào tin nhắn 'Người dùng' ngay trước đó trong lịch sử, trích dẫn lại nó và thêm một bình luận thông minh hoặc hài hước. Ví dụ: 'Ông vừa nhắn đúng một chữ gọn lỏn: “hi” 😎. Đúng kiểu test xem tôi có bật lại không ấy.'\n");
        promptBuilder.append("4. Giữ vững phong cách cá tính, thông minh và thân thiện của bạn. Thêm icon (emoji) phù hợp vào cuối câu trả lời nhé!\n\n");
        
        promptBuilder.append("Câu trả lời của bạn (với phong cách của một người bạn AI cá tính):");

        return promptBuilder.toString();
    }
}