package com.example.demo.service.chat.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.demo.service.chat.tools.SerperWebSearchEngine;
import com.example.demo.service.chat.tools.StockTool;
import com.example.demo.service.chat.tools.TimeTool;
import com.example.demo.service.chat.tools.WeatherTool;

// Thêm các import cần thiết
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

@Component
@Slf4j
public class AgentTools {

    // Các service "Agent" đã được tái cấu trúc
    private final RAGService ragService;
    private final ChitChatService chitChatService;
    private final MemoryQueryService memoryQueryService;

    // Các tool tiện ích hiện có
    private final WeatherTool weatherTool;
    private final TimeTool timeTool;
    private final StockTool stockTool;
    private final SerperWebSearchEngine webSearchEngine;
    
    // --- BẮT ĐẦU THAY ĐỔI ---

    // 1. Khai báo một interface cho trợ lý tìm kiếm OpenAI
    interface OpenAIWebSearchAssistant {
        String search(String query);
    }

    // 2. Khai báo biến cho trợ lý
    private final OpenAIWebSearchAssistant openAIWebSearchAssistant;

    // 3. Cập nhật Constructor để inject ChatLanguageModel và khởi tạo trợ lý
    public AgentTools(
            RAGService ragService,
            ChitChatService chitChatService,
            MemoryQueryService memoryQueryService,
            WeatherTool weatherTool,
            TimeTool timeTool,
            StockTool stockTool,
            SerperWebSearchEngine webSearchEngine,
            ChatLanguageModel chatLanguageModel // <-- Inject ChatLanguageModel vào đây
    ) {
        this.ragService = ragService;
        this.chitChatService = chitChatService;
        this.memoryQueryService = memoryQueryService;
        this.weatherTool = weatherTool;
        this.timeTool = timeTool;
        this.stockTool = stockTool;
        this.webSearchEngine = webSearchEngine;
        
        // Khởi tạo trợ lý tìm kiếm OpenAI bằng AiServices
        this.openAIWebSearchAssistant = AiServices.create(OpenAIWebSearchAssistant.class, chatLanguageModel);
    }
    
    // --- KẾT THÚC THAY ĐỔI ---

    // 4. Thêm một Tool mới sử dụng trợ lý OpenAI
    @Tool("Sử dụng OpenAI để thực hiện tìm kiếm web nâng cao và trả lời các câu hỏi phức tạp cần suy luận từ thông tin trên mạng.")
    public String searchWebWithOpenAI(String query) {
        log.info(">>> Calling OpenAI WebSearch Tool with query: '{}'", query);
        return openAIWebSearchAssistant.search(query);
    }
    
    @Tool("Tìm kiếm trên Internet để trả lời các câu hỏi về kiến thức chung, sự kiện, người nổi tiếng, hoặc thông tin không có trong các công cụ chuyên dụng khác (sử dụng Serper).")
    public String searchWeb(String query) {
        log.info(">>> Calling WebSearch Tool with query: '{}'", query);
        WebSearchResults results = webSearchEngine.search(WebSearchRequest.from(query));
        String searchResultText = results.results().stream()
                .limit(3)
                .map(result -> "Title: " + result.title() + "\nSnippet: " + result.snippet())
                .collect(Collectors.joining("\n\n"));
        return searchResultText.isEmpty() ? "Không tìm thấy kết quả nào trên Internet." : searchResultText;
    }

    // ... các phương thức Tool khác giữ nguyên
    @Tool("Sử dụng khi cần trả lời các câu hỏi dựa trên nội dung các tài liệu, file, văn bản đã được cung cấp và lưu trữ trong cơ sở kiến thức. KHÔNG dùng cho các câu hỏi chung chung hoặc cần thông tin thời gian thực.")
    public String useRAGAgent(String query, @MemoryId Long sessionId) {
        log.info(">>> Calling RAGAgent Tool for session {} with query: '{}'", sessionId, query);
        // ragService của bạn sẽ thực hiện việc tìm kiếm trong PgVector và tạo câu trả lời
        return ragService.answerFromDocuments(query, sessionId);
    }

    @Tool("Sử dụng cho các cuộc trò chuyện thông thường, chào hỏi, tạm biệt...")
    public String useChitChatAgent(String query, @MemoryId Long sessionId) { 
        log.info(">>> Calling ChitChatAgent Tool for session {} with query: '{}'", sessionId, query);
        return chitChatService.chitChat(query, sessionId);
    }

    @Tool("Sử dụng để trả lời các câu hỏi về các tin nhắn đã trò chuyện gần đây...")
    public String useMemoryQueryAgent(String query, @MemoryId Long sessionId) {
        log.info(">>> Calling MemoryQueryAgent Tool for session {} with query: '{}'", sessionId, query);
        return memoryQueryService.answerFromHistory(query, sessionId);
    }

    @Tool("Lấy thông tin thời tiết hiện tại cho một thành phố cụ thể.")
    public String getWeather(String city) {
        log.info(">>> Calling WeatherTool with city: '{}'", city);
        return weatherTool.getWeather(city);
    }
    
    @Tool("Lấy thông tin thời gian hiện tại.")
    public String getCurrentTime() {
        log.info(">>> Calling TimeTool");
        return timeTool.getCurrentTime();
    }
    
    @Tool("Lấy giá cổ phiếu cho một mã chứng khoán (ví dụ: GOOGL, MSFT).")
    public String getStockPrice(String symbol) {
        log.info(">>> Calling StockTool with symbol: '{}'", symbol);
        return stockTool.getStockQuote(symbol);
    }
}