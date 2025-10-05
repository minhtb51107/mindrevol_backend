package com.example.demo.service.audio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@Service
public class AudioTranscriptionService {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private static final String OPENAI_TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions";

    public AudioTranscriptionService(OkHttpClient httpClient,
                                     @Value("${langchain.chat-model.openai.api-key}") String apiKey) {
        this.httpClient = httpClient;
        this.gson = new Gson();
        this.apiKey = apiKey;
    }

    public String transcribe(MultipartFile audioFile) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("file", audioFile.getOriginalFilename(),
                        RequestBody.create(audioFile.getBytes(), MediaType.parse(Objects.requireNonNull(audioFile.getContentType())))
                )
                .build();

        Request request = new Request.Builder()
                .url(OPENAI_TRANSCRIPTION_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = response.body() != null ? response.body().string() : "Empty error body";
                throw new IOException("Unexpected code " + response + ". Body: " + errorBody);
            }

            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            return jsonObject.get("text").getAsString();
        }
    }
}
