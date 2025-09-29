package com.example.demo.service.chat.context;

import com.example.demo.model.chat.ChatMessage;
import com.example.demo.service.chat.integration.TrackedChatLanguageModel; // ✅ 1. IMPORT
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextCompressionService {

    private final ChatLanguageModel chatLanguageModel;

    public String compressContext(List<ChatMessage> messages, String currentQuery) {
        if (messages.isEmpty()) return "";

        StringBuilder contextBuilder = new StringBuilder();
        for (ChatMessage msg : messages) {
            contextBuilder.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }
        String context = contextBuilder.toString();

        if (context.length() < 1000) {
            return context;
        }

        try {
            String systemPrompt = "Bạn là trợ lý tóm tắt ngữ cảnh. Hãy tóm tắt đoạn hội thoại sau thành một bản tóm tắt ngắn gọn, " +
                    "giữ lại thông tin quan trọng nhất liên quan đến câu hỏi hiện tại: '" + currentQuery + "'." +
                    "Chỉ trả về bản tóm tắt, không thêm giải thích.";
            
            // ✅ 2. XÂY DỰNG MESSAGE VÀ GỌI VỚI CALL IDENTIFIER
            List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(context)
            );

            Response<AiMessage> response;
            if (chatLanguageModel instanceof TrackedChatLanguageModel) {
                response = ((TrackedChatLanguageModel) chatLanguageModel).generate(lc4jMessages, "context_compression_chat");
            } else {
                log.warn("Cost tracking for 'context_compression_chat' is skipped.");
                response = chatLanguageModel.generate(lc4jMessages);
            }
            return response.content().text();

        } catch (Exception e) {
            log.warn("Context compression failed, using fallback", e);
            return getImportantMessages(messages, currentQuery);
        }
    }

    public String compressDocumentContext(List<TextSegment> documents, String currentQuery) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        String contextString = documents.stream()
                .map(TextSegment::text)
                .collect(Collectors.joining("\n---\n"));

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
            
            // ✅ 3. XÂY DỰNG MESSAGE VÀ GỌI VỚI CALL IDENTIFIER
            List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("--- NGỮ CẢNH CẦN TÓM TẮT ---\n" + contextString)
            );

            Response<AiMessage> response;
            if (chatLanguageModel instanceof TrackedChatLanguageModel) {
                response = ((TrackedChatLanguageModel) chatLanguageModel).generate(lc4jMessages, "context_compression_docs");
            } else {
                log.warn("Cost tracking for 'context_compression_docs' is skipped.");
                response = chatLanguageModel.generate(lc4jMessages);
            }

            String compressedContext = response.content().text();
            log.info("Context compressed successfully. Original length: {} chars, Compressed length: {} chars", contextString.length(), compressedContext.length());
            return compressedContext;

        } catch (Exception e) {
            log.warn("Document context compression failed due to an error. Using fallback (truncation).", e);
            return contextString.substring(0, Math.min(contextString.length(), CONTEXT_LENGTH_THRESHOLD));
        }
    }

    private String getImportantMessages(List<ChatMessage> messages, String query) {
        return messages.stream()
            .filter(msg -> msg.getSimilarityScore() != null && msg.getSimilarityScore() > 0.7)
            .map(msg -> msg.getSender() + ": " + msg.getContent())
            .collect(Collectors.joining("\n"));
    }
}