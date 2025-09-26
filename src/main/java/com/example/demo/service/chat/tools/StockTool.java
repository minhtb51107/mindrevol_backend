// src/main/java/com/example/demo/service/chat/tools/StockTool.java
package com.example.demo.service.chat.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class StockTool {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final Gson gson = new Gson();

    public StockTool(RestTemplate restTemplate, @Value("${fmp.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Tool("Get the current stock price for a given stock symbol.")
    public String getStockPrice(String symbol) {
        String url = "https://financialmodelingprep.com/api/v3/quote-short/" + symbol + "?apikey=" + apiKey;
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonArray jsonResponse = gson.fromJson(response, JsonArray.class);
            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                JsonObject stockData = jsonResponse.get(0).getAsJsonObject();
                double price = stockData.get("price").getAsDouble();
                return "The current price of " + symbol + " is $" + price;
            }
            return "Could not find stock price for " + symbol;
        } catch (Exception e) {
            return "Error retrieving stock information for " + symbol;
        }
    }
}