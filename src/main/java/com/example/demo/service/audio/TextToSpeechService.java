package com.example.demo.service.audio;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class TextToSpeechService {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private static final String OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech";

    // Sử dụng @Autowired constructor để inject các bean và giá trị từ application.yml
    public TextToSpeechService(OkHttpClient httpClient, 
                               @Value("${langchain.chat-model.openai.api-key}") String apiKey) {
        this.httpClient = httpClient;
        this.gson = new Gson();
        this.apiKey = apiKey;
    }

    /**
     * Chuyển đổi văn bản thành giọng nói bằng cách gọi trực tiếp API của OpenAI.
     * @param text Văn bản cần chuyển đổi.
     * @return Dữ liệu âm thanh dạng mảng byte (định dạng mp3).
     * @throws IOException Nếu có lỗi trong quá trình gọi API.
     */
    public byte[] speak(String text) throws IOException {
        // 1. Tạo body cho request
        Map<String, String> bodyMap = Map.of(
            "model", "tts-1", // Có thể dùng "tts-1-hd" cho chất lượng cao hơn
            "input", text,
            "voice", "alloy"
        );
        String jsonBody = gson.toJson(bodyMap);

        // 2. Tạo request
        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(OPENAI_TTS_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        // 3. Thực thi request và xử lý response
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                // Ghi lại lỗi chi tiết từ OpenAI để dễ dàng debug
                String errorBody = response.body() != null ? response.body().string() : "Empty error body";
                throw new IOException("Unexpected code " + response + ". Body: " + errorBody);
            }
            // Trả về dữ liệu âm thanh
            return response.body().bytes();
        }
    }
}
