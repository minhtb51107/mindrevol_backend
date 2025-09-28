package com.example.demo.service.chat.tools;

import com.example.demo.service.CostTrackingService; // ✅ 1. IMPORT SERVICE
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.web.search.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal; // ✅ 1. IMPORT BIGDECIMAL
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j // Thêm logger để ghi lại thông tin
@Component
public class SerperWebSearchEngine implements WebSearchEngine {

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String apiKey;
    private final CostTrackingService costTrackingService; // ✅ 2. THÊM TRƯỜNG MỚI

    // ✅ 4. THÊM HẰNG SỐ GIÁ
    // LƯU Ý: Giá này chỉ là tham khảo. Hãy kiểm tra lại trên trang giá của Serper.
    private static final BigDecimal COST_PER_QUERY = new BigDecimal("0.001"); // $1 cho 1000 lượt search

    // ✅ 3. CẬP NHẬT CONSTRUCTOR
    public SerperWebSearchEngine(@Value("${serper.api.key}") String apiKey, CostTrackingService costTrackingService) {
        this.apiKey = apiKey;
        this.costTrackingService = costTrackingService;
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        String query = webSearchRequest.searchTerms();

        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("q", query);

            Request request = new Request.Builder()
                    .url("https://google.serper.dev/search")
                    .post(RequestBody.create(requestBody.toString(),
                            okhttp3.MediaType.parse("application/json")))
                    .addHeader("X-API-KEY", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {

                // --- BẮT ĐẦU TÍCH HỢP THEO DÕI CHI PHÍ ---
                // Ghi nhận chi phí ngay khi thực hiện lời gọi API, bất kể thành công hay thất bại,
                // vì Serper vẫn có thể tính phí cho mỗi request.
                costTrackingService.recordUsage("SERPER_SEARCH", "queries", 1L, COST_PER_QUERY);
                log.info("Executed a Serper web search for query: '{}'. Estimated cost: ${}", query, COST_PER_QUERY);
                // --- KẾT THÚC TÍCH HỢP ---
                
                if (!response.isSuccessful()) {
                    log.error("Serper API call failed with code: {}", response.code());
                    return createEmptyResults();
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                JsonArray organicResultsJson = jsonResponse.getAsJsonArray("organic");
                List<WebSearchOrganicResult> organicResults = new ArrayList<>();

                if (organicResultsJson != null) {
                    for (JsonElement resultElement : organicResultsJson) {
                        JsonObject resultObject = resultElement.getAsJsonObject();
                        
                        String title = resultObject.has("title") ? resultObject.get("title").getAsString() : "";
                        String link = resultObject.has("link") ? resultObject.get("link").getAsString() : "";
                        String snippet = resultObject.has("snippet") ? resultObject.get("snippet").getAsString() : null;

                        try {
                            URI uri = new URI(link);
                            WebSearchOrganicResult organicResult = new WebSearchOrganicResult(title, uri, snippet, null);
                            organicResults.add(organicResult);
                        } catch (URISyntaxException e) {
                            log.warn("Skipping result due to invalid URI: {}", link);
                        }
                    }
                }

                long totalResults = 0;
                if (jsonResponse.has("searchInformation")) {
                    JsonObject searchInfoJson = jsonResponse.getAsJsonObject("searchInformation");
                    if (searchInfoJson.has("totalResults")) {
                        totalResults = searchInfoJson.get("totalResults").getAsLong();
                    }
                }
                WebSearchInformationResult searchInformation = WebSearchInformationResult.from(totalResults);

                return new WebSearchResults(searchInformation, organicResults);
            }
        } catch (IOException e) {
            log.error("IOException during Serper API call", e);
            return createEmptyResults();
        }
    }

    private WebSearchResults createEmptyResults() {
        WebSearchInformationResult emptyInfo = WebSearchInformationResult.from(0L);
        return new WebSearchResults(emptyInfo, Collections.emptyList());
    }
}