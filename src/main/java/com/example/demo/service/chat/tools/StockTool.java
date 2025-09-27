// src/main/java/com/example/demo/service/chat/tools/StockTool.java
package com.example.demo.service.chat.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class StockTool {

    private static final Logger log = LoggerFactory.getLogger(StockTool.class);
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final Gson gson = new Gson();

    public StockTool(RestTemplate restTemplate, @Value("${finnhub.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Tool("Tìm kiếm và cung cấp thông tin giá chứng khoán cho một mã (symbol) công ty.")
    public String getStockQuote(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return "Lỗi: Mã chứng khoán không được để trống.";
        }
        
        try {
            // Bước 1: Tìm kiếm mã chứng khoán
            String searchUrl = String.format("https://finnhub.io/api/v1/search?q=%s&token=%s", symbol.toUpperCase(), apiKey);
            String searchResponse = restTemplate.getForObject(searchUrl, String.class);
            JsonObject searchResult = gson.fromJson(searchResponse, JsonObject.class);

            if (searchResult == null || !searchResult.has("result") || searchResult.get("result").getAsJsonArray().isEmpty()) {
                return "Không tìm thấy công ty nào khớp với mã '" + symbol + "'.";
            }

            // ✅ LOGIC MỚI: Ưu tiên tìm mã của Việt Nam (.VN)
            JsonArray results = searchResult.get("result").getAsJsonArray();
            JsonObject bestMatch = null;
            for (int i = 0; i < results.size(); i++) {
                JsonObject currentMatch = results.get(i).getAsJsonObject();
                if (currentMatch.get("symbol").getAsString().endsWith(".VN")) {
                    bestMatch = currentMatch;
                    break; // Tìm thấy mã VN, chọn ngay và dừng lại
                }
            }

            // Nếu không có mã .VN, lấy kết quả đầu tiên
            if (bestMatch == null) {
                bestMatch = results.get(0).getAsJsonObject();
            }

            String correctSymbol = bestMatch.get("symbol").getAsString();
            String description = bestMatch.get("description").getAsString();
            log.info("Đã chọn mã phù hợp nhất cho '{}' là '{}' ({})", symbol, correctSymbol, description);

            // Bước 2: Dùng mã đã chọn để lấy giá
            String quoteUrl = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", correctSymbol, apiKey);
            String quoteResponse = restTemplate.getForObject(quoteUrl, String.class);
            JsonObject quoteData = gson.fromJson(quoteResponse, JsonObject.class);

            if (quoteData != null && quoteData.has("c") && quoteData.get("c").getAsDouble() != 0) {
                double currentPrice = quoteData.get("c").getAsDouble();
                double change = quoteData.get("d").getAsDouble();
                double changePercent = quoteData.get("dp").getAsDouble();

                // Chuyển đổi giá sang VND nếu là mã Việt Nam
                if (correctSymbol.endsWith(".VN")) {
                    // Giả sử 1 USD = 25,000 VND (bạn có thể thay đổi tỷ giá này)
                    double priceInVND = currentPrice * 25000;
                    double changeInVND = change * 25000;
                     return String.format(
                        "Thông tin cho %s (%s): Giá hiện tại là %,.0f VND. Thay đổi trong ngày: %,.0f VND (%.2f%%).",
                        description, correctSymbol, priceInVND, changeInVND, changePercent
                    );
                }

                return String.format(
                    "Thông tin cho %s (%s): Giá hiện tại là $%.2f. Thay đổi trong ngày: %.2f (%.2f%%).",
                    description, correctSymbol, currentPrice, change, changePercent
                );
            } else {
                 return "Đã tìm thấy '" + description + "' nhưng không có dữ liệu giá tại thời điểm này.";
            }

        } catch (HttpClientErrorException.Forbidden e) {
             log.warn("API key không có quyền truy cập dữ liệu giá cho mã này.");
             return "API key của bạn không có quyền truy cập dữ liệu cho sàn giao dịch này. Vui lòng kiểm tra lại gói dịch vụ API.";
        } catch (Exception e) {
            log.error("Lỗi khi truy xuất dữ liệu từ Finnhub cho mã '{}': {}", symbol, e.getMessage());
            return "Đã xảy ra lỗi hệ thống khi lấy dữ liệu từ Finnhub cho mã: " + symbol;
        }
    }
}