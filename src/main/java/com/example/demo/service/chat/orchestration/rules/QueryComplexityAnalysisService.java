package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.service.AiServices;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueryComplexityAnalysisService {

    // Interface này định nghĩa cách chúng ta muốn LLM trả về kết quả có cấu trúc.
    // LangChain4j sẽ tự động xử lý việc chuyển đổi từ text sang object Java.
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplexityClassification {
        private QueryComplexity complexity;
        private String reason;
    }

    // Đây là "Service" được tạo bởi LangChain4j.
    // Nó sử dụng annotation @StructuredPrompt để tạo ra một prompt hoàn chỉnh.
    @StructuredPrompt({
        "You are a master at analyzing user queries to determine their complexity for a Large Language Model.",
        "Your task is to classify the user's query as either SIMPLE or COMPLEX based on the following rules:",
        "1. A query is SIMPLE if it can be answered by a single, straightforward tool call. Examples: 'what time is it?', 'what is the weather in Hanoi?', 'what is the stock price of AAPL?'.",
        "2. A query is COMPLEX if it requires multiple tool calls, data comparison, synthesis, analysis, summarization, or deep reasoning. Examples: 'Compare the stock performance of Tesla and Google over the last quarter and summarize the key events affecting their prices.', 'Based on the attached document, what are the main risks and how can we mitigate them?'",
        "User Query: '{{query}}'",
        "Provide your classification and a brief, one-sentence reason."
    })
    interface ComplexityClassifier {
        ComplexityClassification classify(String query);
    }

    private final ComplexityClassifier classifier;

    /**
     * Constructor này inject vào mô hình ngôn ngữ chi phí thấp (GPT-3.5)
     * và sử dụng AiServices của LangChain4j để tạo ra một triển khai (implementation)
     * cho interface ComplexityClassifier.
     *
     * @param chatLanguageModel Bean có tên "basicChatLanguageModel".
     */
    public QueryComplexityAnalysisService(@Qualifier("basicChatLanguageModel") ChatLanguageModel chatLanguageModel) {
        this.classifier = AiServices.create(ComplexityClassifier.class, chatLanguageModel);
    }

    /**
     * Phân tích câu hỏi của người dùng và trả về độ phức tạp.
     *
     * @param userQuery Câu hỏi gốc từ người dùng.
     * @return Enum QueryComplexity (SIMPLE hoặc COMPLEX).
     */
    public QueryComplexity analyze(String userQuery) {
        try {
            ComplexityClassification classification = this.classifier.classify(userQuery);
            log.info("Query complexity for '{}' -> {}. Reason: {}", userQuery, classification.getComplexity(), classification.getReason());
            return classification.getComplexity();
        } catch (Exception e) {
            log.warn("Failed to classify query complexity for: '{}'. Defaulting to COMPLEX for safety.", userQuery, e);
            // An toàn là trên hết: nếu quá trình phân loại gặp lỗi,
            // chúng ta mặc định coi nó là COMPLEX để mô hình mạnh hơn xử lý.
            return QueryComplexity.COMPLEX;
        }
    }
}