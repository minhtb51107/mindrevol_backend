package com.example.demo.service.chat.agent.tools;

import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineManager;
import com.example.demo.service.chat.tools.SerperWebSearchEngine; // ✅ 1. IMPORT
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    private final PipelineManager pipelineManager;
    private final ChatSessionRepository chatSessionRepository;
    private final LangChainChatMemoryService chatMemoryService;
    private final SerperWebSearchEngine webSearchEngine; // ✅ 2. INJECT CÔNG CỤ TÌM KIẾM
    private final ChatLanguageModel chatLanguageModel; // ✅ 2. THÊM BIẾN MỚI

    @Transactional
    public String answerFromDocuments(String query, Long sessionId) {
        log.info("RAGService invoked for session {} with query: '{}'", sessionId, query);

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found with ID: " + sessionId));
        ChatMemory chatMemory = chatMemoryService.getChatMemory(sessionId);

        RagContext context = RagContext.builder()
                .session(session)
                .user(session.getUser())
                .chatMemory(chatMemory)
                .initialQuery(query)
                .query(query)
                .build();

        RagContext resultContext = pipelineManager.run(context, "default-rag");
        String reply = resultContext.getReply();

        if (reply == null || reply.isEmpty() || isUnhelpfulAnswer(reply)) {
            log.warn("No result from RAG for session {}. Activating AUTOMATIC fallback to web search.", sessionId);
            
            WebSearchResults results = webSearchEngine.search(WebSearchRequest.from(query));
            String searchResultText = results.results().stream()
                    .limit(3)
                    .map(result -> "Title: " + result.title() + "\nSnippet: " + result.snippet())
                    .collect(Collectors.joining("\n\n"));
            
            if (searchResultText.isEmpty()) {
                return "Tôi đã thử tìm trong tài liệu và cả trên Internet nhưng đều không thấy thông tin phù hợp.";
            }

            // ✅ 3. LOGIC MỚI: DÙNG LLM ĐỂ TỔNG HỢP KẾT QUẢ TÌM KIẾM
            // Tạo một prompt hướng dẫn AI cách trả lời.
            String prompt = String.format(
                "Dựa vào những thông tin tìm kiếm được từ Internet dưới đây:\n\n" +
                "--- Begin Search Results ---\n" +
                "%s\n" +
                "--- End Search Results ---\n\n" +
                "Hãy trả lời câu hỏi của người dùng một cách tự nhiên, đầy đủ và chi tiết nhất có thể. " +
                "Câu hỏi của người dùng là: \"%s\"",
                searchResultText, query
            );

            log.info("Generating final answer using web search context for session {}", sessionId);
            // Gọi mô hình ngôn ngữ để tạo câu trả lời
            String finalAnswer = chatLanguageModel.generate(prompt);
            
            return finalAnswer; // Trả về câu trả lời đã được AI xử lý
        }

        return reply;
    }

    private boolean isUnhelpfulAnswer(String reply) {
        if (reply == null || reply.isBlank()) {
            return true;
        }
        String lowerCaseReply = reply.toLowerCase();
        return lowerCaseReply.contains("không tìm thấy") ||
               lowerCaseReply.contains("không có trong cơ sở kiến thức") ||
               lowerCaseReply.contains("tôi không thể giúp");
    }
}