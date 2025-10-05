package com.example.demo.service.interpreter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CodeInterpreterService {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;

    // QUAN TRỌNG: Dán Assistant ID bạn đã tạo ở Bước 1 vào đây
    @Value("${openai.assistant.id}")
    private String assistantId;

    private static final String OPENAI_API_BASE_URL = "https://api.openai.com/v1";

    public CodeInterpreterService(OkHttpClient httpClient,
                                @Value("${langchain.chat-model.openai.api-key}") String apiKey) {
        this.httpClient = httpClient;
        this.gson = new Gson();
        this.apiKey = apiKey;
    }

    /**
     * Tạo một luồng hội thoại (thread) mới cho Code Interpreter.
     * @return ID của luồng mới.
     */
    public String createThread() throws IOException {
        Request request = new Request.Builder()
                .url(OPENAI_API_BASE_URL + "/threads")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2") // Header bắt buộc cho v2
                .post(RequestBody.create(new byte[0]))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to create thread: " + response);
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return json.get("id").getAsString();
        }
    }

    /**
     * Thực thi một câu lệnh trong một luồng đã có và trả về câu trả lời.
     * @param threadId ID của luồng hội thoại.
     * @param userInput Câu lệnh hoặc câu hỏi của người dùng.
     * @return Câu trả lời từ Assistant.
     */
    public String execute(String threadId, String userInput) throws IOException, InterruptedException {
        // Bước 1: Thêm tin nhắn của người dùng vào luồng
        addMessageToThread(threadId, userInput);

        // Bước 2: Ra lệnh cho Assistant chạy trên luồng này
        String runId = createRun(threadId);

        // Bước 3: Chờ cho đến khi quá trình chạy hoàn tất (polling)
        waitForRunCompletion(threadId, runId);

        // Bước 4: Lấy tin nhắn trả lời mới nhất từ Assistant
        return getLatestAssistantResponse(threadId);
    }

    private void addMessageToThread(String threadId, String content) throws IOException {
        Map<String, String> message = Map.of("role", "user", "content", content);
        String jsonBody = gson.toJson(message);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(OPENAI_API_BASE_URL + "/threads/" + threadId + "/messages")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to add message to thread: " + response);
        }
    }

    private String createRun(String threadId) throws IOException {
        Map<String, String> bodyMap = Map.of("assistant_id", this.assistantId);
        String jsonBody = gson.toJson(bodyMap);
        RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(OPENAI_API_BASE_URL + "/threads/" + threadId + "/runs")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to create run: " + response);
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return json.get("id").getAsString();
        }
    }

    private void waitForRunCompletion(String threadId, String runId) throws IOException, InterruptedException {
        while (true) {
            Request request = new Request.Builder()
                    .url(OPENAI_API_BASE_URL + "/threads/" + threadId + "/runs/" + runId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("OpenAI-Beta", "assistants=v2")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Failed to retrieve run status: " + response);
                JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                String status = json.get("status").getAsString();

                log.info("Run ID {} in thread {} has status: {}", runId, threadId, status);

                if ("completed".equals(status)) {
                    return; // Hoàn thành
                }
                if ("failed".equals(status) || "cancelled".equals(status) || "expired".equals(status)) {
                    throw new IOException("Run failed with status: " + status);
                }
                
                // Chờ 1 giây trước khi kiểm tra lại
                TimeUnit.SECONDS.sleep(1);
            }
        }
    }
    
    private String getLatestAssistantResponse(String threadId) throws IOException {
        // Lấy danh sách các tin nhắn trong luồng, chỉ lấy tin nhắn mới nhất
        Request request = new Request.Builder()
                .url(OPENAI_API_BASE_URL + "/threads/" + threadId + "/messages?limit=1")
                .header("Authorization", "Bearer " + apiKey)
                .header("OpenAI-Beta", "assistants=v2")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Failed to list messages: " + response);
            
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            JsonArray data = json.getAsJsonArray("data");
            
            if (data != null && data.size() > 0) {
                JsonObject latestMessage = data.get(0).getAsJsonObject();
                // Tin nhắn trả về có thể có nhiều phần (content block)
                JsonArray contentArray = latestMessage.getAsJsonArray("content");
                if (contentArray != null && contentArray.size() > 0) {
                    // Chúng ta chỉ lấy phần text đầu tiên
                    JsonObject textObject = contentArray.get(0).getAsJsonObject().getAsJsonObject("text");
                    return textObject.get("value").getAsString();
                }
            }
            return "Không có câu trả lời từ Assistant.";
        }
    }
}
