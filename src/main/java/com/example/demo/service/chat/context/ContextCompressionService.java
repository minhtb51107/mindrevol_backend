package com.example.demo.service.chat.context;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.demo.model.chat.ChatMessage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressionService {
    
    private final ChatLanguageModel chatLanguageModel;
    
    public String compressContext(List<ChatMessage> messages, String currentQuery) {
        if (messages.isEmpty()) return "";
        
        // Xây dựng prompt cho LLM tóm tắt
        StringBuilder contextBuilder = new StringBuilder();
        for (ChatMessage msg : messages) {
            contextBuilder.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }
        
        String context = contextBuilder.toString();
        
        // Nếu context ngắn thì không cần nén
        if (context.length() < 1000) {
            return context;
        }
        
        try {
            String systemPrompt = "Bạn là trợ lý tóm tắt ngữ cảnh. Hãy tóm tắt đoạn hội thoại sau thành một bản tóm tắt ngắn gọn, " +
                    "giữ lại thông tin quan trọng nhất liên quan đến câu hỏi hiện tại: '" + currentQuery + "'." +
                    "Chỉ trả về bản tóm tắt, không thêm giải thích.";
            
            String userPrompt = context;
            
            // Sử dụng ChatLanguageModel để gọi API
            String compressedContext = chatLanguageModel.generate(
                systemPrompt + "\n\n" + userPrompt
            );
            
            return compressedContext;
            
        } catch (Exception e) {
            log.warn("Context compression failed, using fallback", e);
            // Fallback: lấy các message quan trọng nhất
            return getImportantMessages(messages, currentQuery);
        }
    }
    
    /**
     * ✅ LOGIC MỚI: Nén danh sách các TextSegment được truy xuất.
     * Nếu tổng độ dài vượt quá ngưỡng, phương thức này sẽ dùng LLM để tóm tắt
     * các văn bản dựa trên truy vấn của người dùng.
     *
     * @param documents Danh sách các TextSegment cần được nén.
     * @param currentQuery Truy vấn hiện tại của người dùng, dùng làm kim chỉ nam cho việc tóm tắt.
     * @return Một chuỗi ngữ cảnh duy nhất, đã được nén nếu cần.
     */
    public String compressDocumentContext(List<TextSegment> documents, String currentQuery) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        String contextString = documents.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n---\n"));

        // Ngưỡng an toàn (ví dụ: 16000 ký tự) để tránh vượt giới hạn token của model.
        // Nên điều chỉnh giá trị này cho phù hợp với model bạn đang sử dụng.
        final int CONTEXT_LENGTH_THRESHOLD = 16000;

        if (contextString.length() < CONTEXT_LENGTH_THRESHOLD) {
            log.debug("Document context length ({}) is under the threshold. No compression needed.", contextString.length());
            return contextString;
        }

        log.info("Document context length ({}) exceeds threshold ({}). Compressing context...", contextString.length(), CONTEXT_LENGTH_THRESHOLD);
        try {
            String systemPrompt = String.format(
                "Bạn là một trợ lý AI chuyên về việc tóm tắt và nén ngữ cảnh. " +
                "Hãy tóm tắt nội dung dưới đây một cách súc tích, chỉ giữ lại những thông tin cốt lõi và quan trọng nhất có liên quan trực tiếp đến truy vấn của người dùng: '%s'. " +
                "Loại bỏ mọi thông tin không liên quan. " +
                "Chỉ trả về bản tóm tắt cuối cùng, không thêm bất kỳ lời dẫn hay giải thích nào.",
                currentQuery
            );

            // Gửi toàn bộ ngữ cảnh để LLM thực hiện tóm tắt
            String compressedContext = chatLanguageModel.generate(systemPrompt + "\n\n--- NGỮ CẢNH CẦN TÓM TẮT ---\n" + contextString);
            log.info("Context compressed successfully. Original length: {} chars, Compressed length: {} chars", contextString.length(), compressedContext.length());
            return compressedContext;

        } catch (Exception e) {
            log.warn("Document context compression failed due to an error. Using fallback (truncation) to prevent failure.", e);
            // Phương án dự phòng an toàn: Cắt bớt ngữ cảnh để đảm bảo API call không thất bại.
            return contextString.substring(0, Math.min(contextString.length(), CONTEXT_LENGTH_THRESHOLD));
        }
    }

    private String getImportantMessages(List<ChatMessage> messages, String query) {
        // ... (Mã hiện có không thay đổi)
        return messages.stream()
            .filter(msg -> msg.getSimilarityScore() != null && msg.getSimilarityScore() > 0.7)
            .map(msg -> msg.getSender() + ": " + msg.getContent())
            .collect(Collectors.joining("\n"));
    }
}