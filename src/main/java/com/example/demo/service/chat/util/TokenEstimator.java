// Khai báo package và import các thư viện cần thiết
package com.example.demo.service.chat.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Đánh dấu đây là một Spring Component
@Component
// Triển khai interface TokenCounter
public class TokenEstimator implements TokenCounter {
    // URL của service tokenizer (chạy trên localhost:5005)
    private static final String TOKENIZER_API_URL = "http://localhost:5005/token-count";
    // ObjectMapper để xử lý JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Phương thức đếm số token trong một đoạn text
    @Override
    public int countTokens(String text) {
        try {
            // Tạo URL object từ API endpoint
            URL url = new URL(TOKENIZER_API_URL);
            // Mở kết nối HTTP
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // Thiết lập method POST
            conn.setRequestMethod("POST");
            // Cho phép gửi dữ liệu output
            conn.setDoOutput(true);
            // Thiết lập header Content-Type
            conn.setRequestProperty("Content-Type", "application/json");

            // Tạo JSON input với text cần đếm token
            // Sử dụng ObjectMapper để escape các ký tự đặc biệt trong text
            String jsonInput = String.format("{\"text\": %s}", objectMapper.writeValueAsString(text));

            // Gửi dữ liệu qua output stream
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8"); // Chuyển thành bytes UTF-8
                os.write(input, 0, input.length); // Ghi dữ liệu
            }

            // Kiểm tra response code
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed to count tokens: HTTP " + conn.getResponseCode());
            }

            // Đọc response từ server
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                // Đọc từng dòng response
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }

                // Parse JSON response
                JsonNode jsonResponse = objectMapper.readTree(response.toString());
                // Lấy giá trị token_count từ response
                return jsonResponse.get("token_count").asInt();
            }

        } catch (Exception e) {
            // Xử lý lỗi và trả về -1 nếu có lỗi
            System.err.println("Lỗi khi đếm token: " + e.getMessage());
            return -1;
        }
    }
}