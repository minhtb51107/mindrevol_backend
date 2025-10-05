package com.example.demo.service.chat.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId; // ✅ QUAN TRỌNG: Import annotation này
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.service.chat.tools.SerperWebSearchEngine;
import com.example.demo.service.chat.tools.StockTool;
import com.example.demo.service.chat.tools.TimeTool;
import com.example.demo.service.chat.tools.WeatherTool;

@Component
@RequiredArgsConstructor
@Slf4j // ✅ THAY ĐỔI: Sử dụng logger chuẩn thay vì System.out.println
public class AgentTools {

    // Các service "Agent" đã được tái cấu trúc
    private final RAGService ragService;
    private final ChitChatService chitChatService;
    private final MemoryQueryService memoryQueryService;

    // Các tool tiện ích hiện có trong dự án của bạn
    private final WeatherTool weatherTool;
    private final TimeTool timeTool;
    private final StockTool stockTool;
    private final SerperWebSearchEngine webSearchEngine; // ✅ INJECT CÔNG CỤ TÌM KIẾM
    
    @Tool("Tìm kiếm trên Internet để trả lời các câu hỏi về kiến thức chung, sự kiện, người nổi tiếng, hoặc thông tin không có trong các công cụ chuyên dụng khác.")
    public String searchWeb(String query) {
        log.info(">>> Calling WebSearch Tool with query: '{}'", query);
        
        // ✅ SỬA LỖI: Dùng WebSearchRequest.from(query) để tạo đối tượng request
        WebSearchResults results = webSearchEngine.search(WebSearchRequest.from(query));
        
        // ✅ SỬA LỖI: Dùng results.results() thay vì organicResults()
        String searchResultText = results.results().stream()
                .limit(3) // Chỉ lấy 3 kết quả hàng đầu để tránh tốn token
                .map(result -> "Title: " + result.title() + "\nSnippet: " + result.snippet())
                .collect(Collectors.joining("\n\n"));
        
        return searchResultText.isEmpty() ? "Không tìm thấy kết quả nào trên Internet." : searchResultText;
    }

    @Tool("Sử dụng khi cần trả lời các câu hỏi dựa trên nội dung các tài liệu, file, văn bản đã được cung cấp và lưu trữ trong cơ sở kiến thức. KHÔNG dùng cho các câu hỏi chung chung hoặc cần thông tin thời gian thực.")
    public String useRAGAgent(String query, @MemoryId Long sessionId) { // ✅ QUAN TRỌNG: Thêm @MemoryId
        log.info(">>> Calling RAGAgent Tool for session {} with query: '{}'", sessionId, query);
        return ragService.answerFromDocuments(query, sessionId);
    }

    @Tool("Sử dụng cho các cuộc trò chuyện thông thường, chào hỏi, tạm biệt, cảm ơn, hoặc các câu hỏi phiếm không có mục đích thông tin rõ ràng (ví dụ: 'Chào bạn', 'Bạn khỏe không?').")
    public String useChitChatAgent(String query, @MemoryId Long sessionId) { // ✅ QUAN TRỌNG: Thêm @MemoryId
        log.info(">>> Calling ChitChatAgent Tool for session {} with query: '{}'", sessionId, query);
        return chitChatService.chitChat(query, sessionId);
    }

    @Tool("Sử dụng để trả lời các câu hỏi về các tin nhắn đã trò chuyện gần đây trong cuộc hội thoại hiện tại.")
    public String useMemoryQueryAgent(String query, @MemoryId Long sessionId) { // ✅ QUAN TRỌNG: Thêm @MemoryId
        log.info(">>> Calling MemoryQueryAgent Tool for session {} with query: '{}'", sessionId, query);
        return memoryQueryService.answerFromHistory(query, sessionId);
    }

    // --- Tích hợp các tool hiện có ---

    @Tool("Lấy thông tin thời tiết hiện tại cho một thành phố cụ thể.")
    public String getWeather(String city) {
        log.info(">>> Calling WeatherTool with city: '{}'", city);
        // Giả sử WeatherTool có phương thức getWeather(String city)
        return weatherTool.getWeather(city);
    }
    
    @Tool("Lấy thông tin thời gian hiện tại.")
    public String getCurrentTime() {
        log.info(">>> Calling TimeTool");
        // Giả sử TimeTool có phương thức getCurrentTime()
        return timeTool.getCurrentTime();
    }
    
    @Tool("Lấy giá cổ phiếu cho một mã chứng khoán (ví dụ: GOOGL, MSFT).")
    public String getStockPrice(String symbol) {
        log.info(">>> Calling StockTool with symbol: '{}'", symbol);
         // Giả sử StockTool có phương thức getStockPrice(String symbol)
        return stockTool.getStockQuote(symbol);
    }
}