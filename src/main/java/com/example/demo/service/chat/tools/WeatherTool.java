// src/main/java/com/example/demo/service/chat/tools/WeatherTool.java
package com.example.demo.service.chat.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component // Đánh dấu đây là một Spring Bean
public class WeatherTool {

    private final RestTemplate restTemplate;
    private final String apiKey;

    // Spring sẽ tự động inject RestTemplate và giá trị từ application.yml vào đây
    public WeatherTool(RestTemplate restTemplate, @Value("${weather.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Tool("Gets the weather forecast for a specific location.")
    public String getWeather(@P("The city name, e.g., 'Hanoi'") String location) {
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric&lang=vi", location, apiKey);

        try {
            // Gọi API và nhận kết quả dưới dạng String (JSON)
            String response = restTemplate.getForObject(url, String.class);
            // Bạn có thể parse JSON để lấy thông tin chi tiết hơn, nhưng trước mắt trả về nguyên chuỗi là đủ để LLM hiểu
            return "Weather data for " + location + ": " + response;
        } catch (Exception e) {
            e.printStackTrace();
            return "Sorry, I could not retrieve the weather information for " + location;
        }
    }
}