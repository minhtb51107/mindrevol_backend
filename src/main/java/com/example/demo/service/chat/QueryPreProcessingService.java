package com.example.demo.service.chat;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueryPreProcessingService {

    private final OpenAiTokenizer tokenizer;
    @Qualifier("summarizationModel") // Sử dụng model tóm tắt rẻ tiền đã tạo ở bước trước
    private final ChatLanguageModel summarizationModel;

    // Ngưỡng token, có thể điều chỉnh trong application.properties
    private static final int TOKEN_THRESHOLD = 500; 

    public String process(String query) {
        int tokenCount = tokenizer.estimateTokenCountInText(query);
        log.info("Estimated token count for user query: {}", tokenCount);

        if (tokenCount > TOKEN_THRESHOLD) {
            log.warn("Query exceeds token threshold ({} > {}). Summarizing before processing.", tokenCount, TOKEN_THRESHOLD);
            
            String prompt = "Tóm tắt đoạn văn sau thành một vài câu ngắn gọn, giữ lại ý chính và cảm xúc cốt lõi. Bắt đầu bản tóm tắt bằng '[Bản tóm tắt]: '.\n\nĐoạn văn: \"" + query + "\"";
            
            String summary = summarizationModel.generate(prompt);
            log.info("Generated summary: {}", summary);
            return summary;
        }

        return query;
    }
}