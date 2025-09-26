// src/main/java/com/example/demo/service/chat/tools/SerperWebSearchEngine.java
package com.example.demo.service.chat.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SerperWebSearchEngine implements WebSearchEngine {

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String apiKey;

    public SerperWebSearchEngine(@Value("${serper.api.key}") String apiKey) {
        this.apiKey = apiKey;
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
                if (!response.isSuccessful()) {
                    System.err.println("Unexpected code " + response);
                    return createEmptyResults();
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                JsonArray organicResultsJson = jsonResponse.getAsJsonArray("organic");
                List<WebSearchOrganicResult> organicResults = new ArrayList<>();

                // ==================== FIX CUỐI CÙNG: SỬ DỤNG CONSTRUCTOR ĐÚNG ====================
                if (organicResultsJson != null) {
                    for (JsonElement resultElement : organicResultsJson) {
                        JsonObject resultObject = resultElement.getAsJsonObject();
                        
                        String title = resultObject.has("title") ? resultObject.get("title").getAsString() : "";
                        String link = resultObject.has("link") ? resultObject.get("link").getAsString() : "";
                        String snippet = resultObject.has("snippet") ? resultObject.get("snippet").getAsString() : null;

                        try {
                            // Cốt lõi: Chuyển đổi String link thành URI và gọi constructor
                            URI uri = new URI(link);
                            WebSearchOrganicResult organicResult = new WebSearchOrganicResult(title, uri, snippet, null);
                            organicResults.add(organicResult);
                        } catch (URISyntaxException e) {
                            // Bỏ qua kết quả nếu URL không hợp lệ
                            System.err.println("Skipping result due to invalid URI: " + link);
                        }
                    }
                }
                // =================================================================================

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
            e.printStackTrace();
            return createEmptyResults();
        }
    }

    private WebSearchResults createEmptyResults() {
        WebSearchInformationResult emptyInfo = WebSearchInformationResult.from(0L);
        return new WebSearchResults(emptyInfo, Collections.emptyList());
    }
}