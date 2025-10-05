//package com.example.demo.service.chat.agent;
//
//import com.example.demo.service.chat.orchestration.context.RagContext;
//import com.example.demo.service.chat.tools.StockTool;
//import dev.langchain4j.data.message.AiMessage;
//import dev.langchain4j.data.message.ChatMessage;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Slf4j
//@Service("FinancialAnalysisWorkflowAgent")
//@RequiredArgsConstructor
//public class FinancialAnalysisWorkflowAgent implements Agent {
//
//    private final StockTool stockTool;
//    private final FinancialAnalystAgent analystAgent;
//
//    @Override
//    public String getName() {
//        return "FinancialAnalysisWorkflowAgent";
//    }
//
//    @Override
//    public String getDescription() {
//        return "Sử dụng khi người dùng hỏi về tình hình, dữ liệu hoặc cần phân tích chuyên sâu về chứng khoán, cổ phiếu, tài chính. Agent này cung cấp cả dữ liệu và nhận định.";
//    }
//
//    @Override
//    public RagContext execute(RagContext context) {
//        String query = context.getInitialQuery();
//        log.info("Executing FinancialAnalysisWorkflowAgent for query: '{}'", query);
//
//        // ✅ BƯỚC 1: KIỂM TRA XEM ĐÂY CÓ PHẢI LÀ YÊU CẦU PHÂN TÍCH NỐI TIẾP KHÔNG
//        if (isFollowUpAnalysisQuestion(query)) {
//            String lastStockData = findLastStockDataInHistory(context.getChatMemory().messages());
//            
//            if (lastStockData != null) {
//                log.info("Detected follow-up analysis question. Using previous stock data.");
//                // Tạo yêu cầu phân tích sâu hơn, cung cấp cả dữ liệu cũ và yêu cầu mới
//                String deeperAnalysisRequest = String.format(
//                        "Dựa trên dữ liệu sau: '%s'. Người dùng muốn phân tích sâu hơn. " +
//                        "Hãy cung cấp thêm các góc nhìn khác, ví dụ như so sánh với hiệu suất ngành, các tin tức gần đây có thể ảnh hưởng, hoặc triển vọng dài hạn dựa trên sự ổn định này.",
//                        lastStockData
//                );
//                String analysisResult = analystAgent.analyzeStockData(deeperAnalysisRequest);
//                context.setReply(analysisResult);
//                return context;
//            }
//        }
//
//        // ✅ BƯỚC 2: NẾU KHÔNG PHẢI CÂU HỎI NỐI TIẾP, THỰC HIỆN QUY TRÌNH NHƯ CŨ
//        String symbol = extractSymbolFromQuery(query);
//        if (symbol == null) {
//            context.setReply("Xin lỗi, tôi không nhận diện được mã chứng khoán trong câu hỏi của bạn. Vui lòng thử lại với một mã cụ thể như 'FPT', 'GOOGL', v.v.");
//            return context;
//        }
//        
//        if ("OpenAI".equalsIgnoreCase(symbol)) {
//            context.setReply("OpenAI là một công ty tư nhân và không được niêm yết trên thị trường chứng khoán, vì vậy không có dữ liệu cổ phiếu để phân tích.");
//            return context;
//        }
//
//        String stockData = stockTool.getStockQuote(symbol);
//
//        if (stockData.toLowerCase().contains("lỗi") || stockData.toLowerCase().contains("không tìm thấy") || stockData.toLowerCase().contains("không có quyền truy cập")) {
//            context.setReply(stockData);
//            return context;
//        }
//
//        String analysis = analystAgent.analyzeStockData(stockData);
//        String finalAnswer = stockData + "\n\n**Phân tích:**\n" + analysis;
//        
//        context.setReply(finalAnswer);
//        return context;
//    }
//
//    // --- CÁC PHƯƠNG THỨC HELPER ---
//
//    private boolean isFollowUpAnalysisQuestion(String query) {
//        String lowerCaseQuery = query.toLowerCase();
//        return lowerCaseQuery.contains("phân tích thêm") || 
//               lowerCaseQuery.contains("sâu hơn") ||
//               lowerCaseQuery.contains("chi tiết hơn") ||
//               lowerCaseQuery.equals("làm đi") || // Xử lý cả trường hợp "làm đi"
//               lowerCaseQuery.contains("cho thấy điều gì");
//    }
//
//    private String findLastStockDataInHistory(List<ChatMessage> history) {
//        for (int i = history.size() - 1; i >= 0; i--) {
//            ChatMessage message = history.get(i);
//            if (message instanceof AiMessage) {
//                String text = message.text();
//                if (text.toLowerCase().contains("giá hiện tại là") && text.toLowerCase().contains("phân tích")) {
//                    // Tách và chỉ trả về phần dữ liệu gốc, không bao gồm phần phân tích cũ
//                    return text.split("\n\n\\*\\*Phân tích:\\*\\*")[0].trim();
//                }
//            }
//        }
//        return null;
//    }
//    
//    private String extractSymbolFromQuery(String query) {
//        String lowerCaseQuery = query.toLowerCase();
//        if (lowerCaseQuery.contains("google") || lowerCaseQuery.contains("googl")) return "GOOGL";
//        if (lowerCaseQuery.contains("fpt")) return "FPT";
//        if (lowerCaseQuery.contains("openai")) return "OpenAI";
//        if (lowerCaseQuery.contains("apple") || lowerCaseQuery.contains("aapl")) return "AAPL";
//        if (lowerCaseQuery.contains("microsoft") || lowerCaseQuery.contains("msft")) return "MSFT";
//        return null;
//    }
//}