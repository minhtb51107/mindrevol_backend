//// Khai báo package và import các thư viện cần thiết
//package com.example.demo.service.chat.integration;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ArrayNode;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.net.URI;
//import java.net.URLEncoder;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicLong;
//import java.util.function.Consumer;
//import java.util.stream.StreamSupport;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//import java.util.concurrent.Flow;
//import java.util.concurrent.SubmissionPublisher;
//import java.util.concurrent.atomic.AtomicReference;
//
//// Đánh dấu đây là một Spring Service
//@Service
//public class OpenAIService {
//
//    // API key cho OpenAI (nên lưu trong configuration)
//    @Value("${openai.api.key}")
//    private String OPENAI_API_KEY;
//
//    // Endpoint API của OpenAI
//    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
//
//    // Rate limiting và token tracking
//    private final AtomicInteger requestCounter = new AtomicInteger(0);
//    private final AtomicLong monthlyTokenUsage = new AtomicLong(0);
//    private final Map<String, AtomicInteger> userRequestCounts = new ConcurrentHashMap<>();
//    private static final int MAX_REQUESTS_PER_MINUTE = 100; // Giới hạn 100 requests mỗi phút
//    private static final long MONTHLY_TOKEN_LIMIT = 1000000; // Giới hạn 1 triệu tokens mỗi tháng
//    private static final int MAX_REQUESTS_PER_USER_PER_HOUR = 50; // Giới hạn 50 requests mỗi user mỗi giờ
//    private LocalDateTime lastResetTime = LocalDateTime.now();
//    private String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
//
//    // Danh sách các function có thể gọi từ AI
//    private static final List<Map<String, Object>> FUNCTIONS = List.of(
//            Map.of("name", "searchWeb", "description", "Tìm kiếm thông tin trên web theo từ khoá người dùng",
//                    "parameters",
//                    Map.of("type", "object", "properties",
//                            Map.of("query",
//                                    Map.of("type", "string", "description", "Câu hỏi hoặc từ khoá để tìm kiếm")),
//                            "required", List.of("query"))),
//            Map.of("name", "getWeather", "description", "Lấy thông tin thời tiết hiện tại tại một địa điểm",
//                    "parameters",
//                    Map.of("type", "object", "properties",
//                            Map.of("location", Map.of("type", "string", "description", "Tên thành phố hoặc địa điểm")),
//                            "required", List.of("location"))),
//            Map.of("name", "getTime", "description", "Lấy thời gian hiện tại của một địa điểm", "parameters",
//                    Map.of("type", "object", "properties",
//                            Map.of("location", Map.of("type", "string", "description", "Tên thành phố hoặc quốc gia")),
//                            "required", List.of("location"))),
//            Map.of("name", "getGeoInfo", "description", "Lấy thông tin địa lý cơ bản của quốc gia", "parameters",
//                    Map.of("type", "object", "properties",
//                            Map.of("location", Map.of("type", "string", "description", "Tên quốc gia")), "required",
//                            List.of("location"))));
//
// // Thêm phương thức streaming trong OpenAIService
//    public CompletableFuture<Void> getChatCompletionStream(
//            List<Map<String, String>> messages, 
//            String model, 
//            int maxTokens, 
//            String userId,
//            Consumer<String> onToken,
//            Consumer<String> onComplete,
//            Consumer<Exception> onError) {
//        
//        return CompletableFuture.runAsync(() -> {
//            try {
//                // Kiểm tra rate limiting
//                checkRateLimits(userId);
//                
//                ObjectMapper mapper = new ObjectMapper();
//                ArrayNode messageArray = mapper.createArrayNode();
//                
//                for (Map<String, String> msg : messages) {
//                    ObjectNode message = mapper.createObjectNode();
//                    message.put("role", msg.get("role"));
//                    message.put("content", msg.get("content"));
//                    messageArray.add(message);
//                }
//
//                // Xây dựng request body với stream=true
//                ObjectNode requestBody = mapper.createObjectNode();
//                requestBody.put("model", model);
//                requestBody.set("messages", messageArray);
//                requestBody.put("temperature", 0.7);
//                requestBody.put("max_tokens", maxTokens);
//                requestBody.put("stream", true); // Quan trọng: bật streaming
//
//                // Tạo HTTP request với streaming
//                HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(API_URL))
//                    .header("Content-Type", "application/json")
//                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
//                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//                    .build();
//
//                // Gửi request và xử lý streaming response
//                HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
//                    .thenAccept(response -> {
//                        if (response.statusCode() != 200) {
//                            onError.accept(new RuntimeException("OpenAI Error: " + response.body()));
//                            return;
//                        }
//
//                        // Xử lý từng dòng trong stream
//                        response.body().forEach(line -> {
//                            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
//                                try {
//                                    String jsonData = line.substring(6);
//                                    JsonNode jsonNode = mapper.readTree(jsonData);
//                                    
//                                    JsonNode choices = jsonNode.get("choices");
//                                    if (choices != null && choices.isArray() && choices.size() > 0) {
//                                        JsonNode delta = choices.get(0).get("delta");
//                                        if (delta != null && delta.has("content")) {
//                                            String token = delta.get("content").asText();
//                                            onToken.accept(token);
//                                        }
//                                    }
//                                } catch (Exception e) {
//                                    onError.accept(e);
//                                }
//                            }
//                        });
//                        
//                        onComplete.accept("Stream completed");
//                    })
//                    .exceptionally(e -> {
//                        onError.accept((Exception) e);
//                        return null;
//                    });
//
//            } catch (Exception e) {
//                onError.accept(e);
//            }
//        });
//    }
//
//    // Phương thức giả lập streaming (nếu OpenAI không hỗ trợ streaming thực sự)
//    public void simulateStreaming(
//            List<Map<String, String>> messages, 
//            String model, 
//            int maxTokens, 
//            String userId,
//            Consumer<String> onToken,
//            Consumer<String> onComplete,
//            Consumer<Exception> onError) {
//        
//        CompletableFuture.runAsync(() -> {
//            try {
//                // Lấy response bình thường
//                String fullResponse = getChatCompletion(messages, model, maxTokens, userId);
//                
//                // Chia nhỏ response thành các token và gửi từng token
//                for (int i = 0; i < fullResponse.length(); i++) {
//                    String token = String.valueOf(fullResponse.charAt(i));
//                    onToken.accept(token);
//                    
//                    // Thêm độ trễ ngẫu nhiên để giống streaming thật
//                    Thread.sleep(10 + (int)(Math.random() * 20));
//                }
//                
//                onComplete.accept(fullResponse);
//                
//            } catch (Exception e) {
//                onError.accept(e);
//            }
//        });
//    }
//    
//    // Phương thức chính để gọi OpenAI API với rate limiting
//    public String getChatCompletion(List<Map<String, String>> messages, String model, int maxTokens) throws Exception {
//        return getChatCompletion(messages, model, maxTokens, "default");
//    }
//
//    public String getChatCompletion(List<Map<String, String>> messages, String model, int maxTokens, String userId) throws Exception {
//        try {
//            // Kiểm tra rate limiting và token usage
//            checkRateLimits(userId);
//            
//            ObjectMapper mapper = new ObjectMapper();
//            ArrayNode messageArray = mapper.createArrayNode();
//            // Chuyển đổi messages thành JSON array
//            for (Map<String, String> msg : messages) {
//                ObjectNode message = mapper.createObjectNode();
//                message.put("role", msg.get("role"));
//                message.put("content", msg.get("content"));
//                messageArray.add(message);
//            }
//
//            // Xây dựng request body
//            ObjectNode requestBody = mapper.createObjectNode();
//            requestBody.put("model", model);
//            requestBody.set("messages", messageArray);
//            requestBody.put("temperature", 0.7); // Độ sáng tạo
//            requestBody.put("max_tokens", maxTokens); // Giới hạn token
//
//            // Tạo HTTP request
//            HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create(API_URL))
//                .header("Content-Type", "application/json")
//                .header("Authorization", "Bearer " + OPENAI_API_KEY)
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//                .build();
//
//            // Gửi request và nhận response
//            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
//            if (response.statusCode() != 200) {
//                throw new RuntimeException("OpenAI Error: " + response.body());
//            }
//
//            // Parse response và trả về nội dung
//            JsonNode jsonResponse = mapper.readTree(response.body());
//            String content = jsonResponse.get("choices").get(0).get("message").get("content").asText();
//            
//            // Cập nhật token usage
//            updateTokenUsage(jsonResponse, userId);
//            
//            return content;
//        } catch (Exception e) {
//            if (e.getMessage().contains("Rate limit") || e.getMessage().contains("Token limit")) {
//                throw e; // Re-throw rate limiting exceptions
//            }
//            throw new RuntimeException("Failed to call OpenAI", e);
//        }
//    }
//
//    // Kiểm tra rate limits và token usage
//    private void checkRateLimits(String userId) {
//        // Reset counter hàng phút
//        if (LocalDateTime.now().isAfter(lastResetTime.plusMinutes(1))) {
//            requestCounter.set(0);
//            lastResetTime = LocalDateTime.now();
//        }
//        
//        // Kiểm tra monthly token limit
//        checkMonthlyTokenLimit();
//        
//        // Kiểm tra global rate limit
//        if (requestCounter.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
//            throw new RuntimeException("Rate limit exceeded: Too many requests. Please try again later.");
//        }
//        
//        // Kiểm tra user-specific rate limit
//        if (userId != null && !userId.equals("default")) {
//            AtomicInteger userCount = userRequestCounts.computeIfAbsent(userId, k -> new AtomicInteger(0));
//            if (userCount.incrementAndGet() > MAX_REQUESTS_PER_USER_PER_HOUR) {
//                throw new RuntimeException("Rate limit exceeded for user: " + userId + ". Please try again in an hour.");
//            }
//        }
//    }
//
//    // Kiểm tra monthly token limit
//    private void checkMonthlyTokenLimit() {
//        // Reset monthly counter nếu tháng mới
//        String newMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
//        if (!newMonth.equals(currentMonth)) {
//            monthlyTokenUsage.set(0);
//            currentMonth = newMonth;
//        }
//        
//        if (monthlyTokenUsage.get() > MONTHLY_TOKEN_LIMIT) {
//            throw new RuntimeException("Monthly token limit exceeded. Please try again next month.");
//        }
//    }
//
//    // Cập nhật token usage từ response
//    private void updateTokenUsage(JsonNode jsonResponse, String userId) {
//        try {
//            JsonNode usageNode = jsonResponse.get("usage");
//            if (usageNode != null && !usageNode.isNull()) {
//                int totalTokens = usageNode.get("total_tokens").asInt();
//                monthlyTokenUsage.addAndGet(totalTokens);
//                
//                // Log token usage for monitoring
//                System.out.println("Token usage updated: " + totalTokens + " tokens. Monthly total: " + monthlyTokenUsage.get());
//            }
//        } catch (Exception e) {
//            System.err.println("Error updating token usage: " + e.getMessage());
//            // Ước tính token usage nếu không có data từ API
//            estimateAndUpdateTokenUsage(jsonResponse);
//        }
//    }
//
//    // Ước tính token usage nếu API không trả về usage data
//    private void estimateAndUpdateTokenUsage(JsonNode jsonResponse) {
//        try {
//            // Ước tính dựa trên độ dài content (1 token ≈ 4 ký tự)
//            JsonNode choicesNode = jsonResponse.get("choices");
//            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
//                JsonNode messageNode = choicesNode.get(0).get("message");
//                if (messageNode != null) {
//                    String content = messageNode.get("content").asText();
//                    int estimatedTokens = content.length() / 4;
//                    monthlyTokenUsage.addAndGet(estimatedTokens);
//                }
//            }
//        } catch (Exception e) {
//            System.err.println("Error estimating token usage: " + e.getMessage());
//        }
//    }
//
//    // Helper method để lấy giá trị an toàn từ JSON
//    private String safeGet(JsonNode node, String fieldName) {
//        JsonNode valueNode = node.get(fieldName);
//        if (valueNode == null || valueNode.isNull()) {
//            throw new RuntimeException("Thiếu trường '" + fieldName + "' trong JSON: " + node.toPrettyString());
//        }
//        return valueNode.asText();
//    }
//
//    // Phương thức xử lý function calling với rate limiting
//    private String callBackWithFunctionResult(List<Map<String, String>> oldMessages, String functionName,
//            String content) throws Exception {
//        List<Map<String, String>> newMessages = new ArrayList<>(oldMessages);
//        newMessages.add(Map.of("role", "assistant", "name", functionName, "content", null));
//        newMessages.add(Map.of("role", "function", "name", functionName, "content", content));
//        return getChatCompletion(newMessages, "gpt-3.5-turbo", 50);
//    }
//
//    // Function: Tìm kiếm web
//    public String searchWeb(String query) {
//        try {
//            String apiUrl = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
//                    + "&format=json";
//            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("Accept", "application/json")
//                    .GET().build();
//
//            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
//                    HttpResponse.BodyHandlers.ofString());
//
//            return "Kết quả từ web: " + response.body();
//        } catch (Exception e) {
//            return "Lỗi khi tìm kiếm web: " + e.getMessage();
//        }
//    }
//
//    // Function: Lấy thông tin thời tiết
//    public String getWeather(String location) {
//        try {
//            String apiKey = "0e5b19799fc85892abc78b032bdc501f"; // Ví dụ dùng OpenWeatherMap
//            String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q="
//                    + URLEncoder.encode(location, StandardCharsets.UTF_8) + "&appid=" + apiKey + "&units=metric";
//            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build();
//
//            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
//                    HttpResponse.BodyHandlers.ofString());
//            JsonNode data = new ObjectMapper().readTree(response.body());
//
//            return String.format("Thời tiết tại %s: %s, %.1f°C", data.get("name").asText(),
//                    data.get("weather").get(0).get("description").asText(), data.get("main").get("temp").asDouble());
//        } catch (Exception e) {
//            return "Lỗi khi lấy thời tiết: " + e.getMessage();
//        }
//    }
//
//    // Function: Lấy thời gian
//    public String getTime(String location) {
//        try {
//            String apiUrl = "http://worldtimeapi.org/api/timezone";
//            HttpResponse<String> response = HttpClient.newHttpClient().send(
//                    HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build(),
//                    HttpResponse.BodyHandlers.ofString());
//
//            // Tìm timezone khớp tên location
//            ArrayNode zones = (ArrayNode) new ObjectMapper().readTree(response.body());
//            Optional<String> zone = StreamSupport.stream(zones.spliterator(), false).map(JsonNode::asText)
//                    .filter(z -> z.toLowerCase().contains(location.toLowerCase())).findFirst();
//
//            if (zone.isPresent()) {
//                String zoneUrl = "http://worldtimeapi.org/api/timezone/" + zone.get();
//                HttpResponse<String> zoneRes = HttpClient.newHttpClient().send(
//                        HttpRequest.newBuilder().uri(URI.create(zoneUrl)).GET().build(),
//                        HttpResponse.BodyHandlers.ofString());
//                JsonNode zoneData = new ObjectMapper().readTree(zoneRes.body());
//                return "Giờ hiện tại tại " + location + ": " + zoneData.get("datetime").asText();
//            }
//
//            return "Không tìm thấy múi giờ cho: " + location;
//        } catch (Exception e) {
//            return "Lỗi khi lấy giờ: " + e.getMessage();
//        }
//    }
//
//    // Function: Lấy thông tin địa lý
//    public String getGeoInfo(String location) {
//        try {
//            String apiUrl = "https://restcountries.com/v3.1/name/"
//                    + URLEncoder.encode(location, StandardCharsets.UTF_8);
//            HttpResponse<String> response = HttpClient.newHttpClient().send(
//                    HttpRequest.newBuilder().uri(URI.create(apiUrl)).GET().build(),
//                    HttpResponse.BodyHandlers.ofString());
//
//            ArrayNode countries = (ArrayNode) new ObjectMapper().readTree(response.body());
//            JsonNode country = countries.get(0);
//
//            return String.format("Tên quốc gia: %s\nThủ đô: %s\nDân số: %s\nKhu vực: %s",
//                    country.get("name").get("common").asText(), country.get("capital").get(0).asText(),
//                    country.get("population").asText(), country.get("region").asText());
//        } catch (Exception e) {
//            return "Lỗi khi lấy thông tin địa lý: " + e.getMessage();
//        }
//    }
//
//    // Phương thức tạo tiêu đề tự động với rate limiting
//    public String getChatCompletion_title(List<ChatMessage> messages) {
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            ArrayNode messagesArray = mapper.createArrayNode();
//
//            for (ChatMessage msg : messages) {
//                ObjectNode messageNode = mapper.createObjectNode();
//                messageNode.put("role", msg.getSender().toLowerCase());
//                messageNode.put("content", msg.getContent());
//                messagesArray.add(messageNode);
//            }
//
//            ObjectNode requestBody = mapper.createObjectNode();
//            requestBody.put("model", "gpt-3.5-turbo");
//            requestBody.set("messages", messagesArray);
//            requestBody.put("temperature", 0.7);
//            requestBody.put("max_tokens", 20); // Giới hạn độ dài tiêu đề
//
//            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/chat/completions"))
//                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + OPENAI_API_KEY)
//                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString())).build();
//
//            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
//                    HttpResponse.BodyHandlers.ofString());
//
//            JsonNode jsonResponse = mapper.readTree(response.body());
//            return jsonResponse.get("choices").get(0).get("message").get("content").asText();
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "Cuộc trò chuyện mới";
//        }
//    }
//
//    // Phương thức lấy embedding vector với rate limiting
//    public List<Double> getEmbedding(String text) {
//        return getEmbedding(text, "default");
//    }
//
//    public List<Double> getEmbedding(String text, String userId) {
//        try {
//            // Kiểm tra rate limiting
//            checkRateLimits(userId);
//            
//            ObjectMapper mapper = new ObjectMapper();
//            ObjectNode requestJson = mapper.createObjectNode();
//            requestJson.put("model", "text-embedding-ada-002");
//            requestJson.put("input", text);
//
//            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.openai.com/v1/embeddings"))
//                    .header("Authorization", "Bearer " + OPENAI_API_KEY).header("Content-Type", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(requestJson.toString())).build();
//
//            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
//                    HttpResponse.BodyHandlers.ofString());
//            JsonNode json = mapper.readTree(response.body());
//
//            JsonNode embeddingArray = json.get("data").get(0).get("embedding");
//
//            List<Double> embedding = new ArrayList<>();
//            for (JsonNode num : embeddingArray) {
//                embedding.add((Double) num.asDouble());
//            }
//            
//            // Cập nhật token usage cho embedding requests
//            updateTokenUsage(json, userId);
//            
//            return embedding;
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Collections.emptyList();
//        }
//    }
//
//    // Tính cosine similarity giữa hai vector
//    public double cosineSimilarity(List<Double> vec1, List<Double> vec2) {
//        if (vec1.size() != vec2.size())
//            return 0;
//
//        double dotProduct = 0.0, norm1 = 0.0, norm2 = 0.0;
//        for (int i = 0; i < vec1.size(); i++) {
//            dotProduct += vec1.get(i) * vec2.get(i);
//            norm1 += Math.pow(vec1.get(i), 2);
//            norm2 += Math.pow(vec2.get(i), 2);
//        }
//
//        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
//    }
//
//    // Tìm các tin nhắn liên quan dựa trên embedding similarity
//    public List<ChatMessage> getRelevantMessages(List<ChatMessage> recentMessages, String currentInput, int topK) {
//        List<Double> currentEmbedding = getEmbedding(currentInput);
//
//        return recentMessages.stream()
//                .map(msg -> Map.entry(msg, cosineSimilarity(getEmbedding(msg.getContent()), currentEmbedding)))
//                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(topK).map(Map.Entry::getKey)
//                .toList();
//    }
//
//    // Biến lưu response cuối cùng
//    private String lastResponseContent = "";
//
//    public String getLastResponseContent() {
//        return lastResponseContent;
//    }
//
//    // Phương thức giả lập streaming response với rate limiting
//    public void streamChatCompletion(List<Map<String, String>> prompt, Consumer<String> tokenConsumer) throws Exception {
//        streamChatCompletion(prompt, tokenConsumer, "default");
//    }
//
// // Thay thế phương thức streamChatCompletion
//    public void streamChatCompletion(List<Map<String, String>> prompt, Consumer<String> tokenConsumer, String userId) throws Exception {
//        // Sử dụng CompletableFuture để chạy trên thread riêng
//        CompletableFuture.runAsync(() -> {
//            try {
//                String response = getChatCompletion(prompt, "gpt-3.5-turbo", 50, userId);
//                
//                for (char c : response.toCharArray()) {
//                    tokenConsumer.accept(String.valueOf(c));
//                    try {
//                        Thread.sleep(20);
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        break;
//                    }
//                }
//                
//                lastResponseContent = response;
//            } catch (Exception e) {
//                tokenConsumer.accept("⚠️ Lỗi khi tạo phản hồi: " + e.getMessage());
//            }
//        });
//    }
//
//    // Phương thức để reset user rate limits (cho testing hoặc admin)
//    public void resetUserRateLimit(String userId) {
//        userRequestCounts.remove(userId);
//    }
//
//    // Phương thức để lấy thông tin token usage
//    public long getMonthlyTokenUsage() {
//        return monthlyTokenUsage.get();
//    }
//
//    // Phương thức để lấy thông tin request count
//    public int getCurrentRequestCount() {
//        return requestCounter.get();
//    }
//}