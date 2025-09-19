//// Khai báo package và import các thư viện cần thiết
//package com.example.demo.service.chat.util;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.SneakyThrows;
//import org.springframework.stereotype.Service;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//
//// Đánh dấu đây là một Spring Service
//@Service
//public class EmbeddingService {
//    // API key cho OpenAI (cần thay thế bằng key thật)
//    private static final String OPENAI_API_KEY = "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"; // ❗Bạn thay bằng khóa thật
//    // Endpoint API của OpenAI để lấy embedding
//    private static final String EMBEDDING_ENDPOINT = "https://api.openai.com/v1/embeddings";
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    // Thời gian cache (phút)
//    private static final long CACHE_EXPIRY_MINUTES = 60;
//
//    // Entry cache bao gồm embedding + thời điểm lưu
//    private static class CacheEntry {
//        final List<Double> embedding;
//        final long timestamp;
//
//        CacheEntry(List<Double> embedding, long timestamp) {
//            this.embedding = embedding;
//            this.timestamp = timestamp;
//        }
//
//        boolean isExpired() {
//            long ageMinutes = (System.currentTimeMillis() - timestamp) / 1000 / 60;
//            return ageMinutes >= CACHE_EXPIRY_MINUTES;
//        }
//    }
//
//    // Cache chính
//    private final ConcurrentHashMap<String, CacheEntry> embeddingCache = new ConcurrentHashMap<>();
//
//    @SneakyThrows
//    public List<Double> getEmbedding(String input) {
//        try {
//            if (input == null || input.isBlank()) {
//                return Collections.emptyList();
//            }
//
//            String cacheKey = Integer.toString(input.hashCode());
//
//            // Kiểm tra cache
//            CacheEntry cached = embeddingCache.get(cacheKey);
//            if (cached != null && !cached.isExpired()) {
//                return cached.embedding;
//            }
//
//            // Nếu chưa có trong cache hoặc cache hết hạn → gọi API
//            String requestBody = mapper.writeValueAsString(Map.of(
//                    "input", input,
//                    "model", "text-embedding-3-small"
//            ));
//
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(EMBEDDING_ENDPOINT))
//                    .header("Authorization", "Bearer " + OPENAI_API_KEY)
//                    .header("Content-Type", "application/json")
//                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                    .build();
//
//            HttpResponse<String> response = HttpClient.newHttpClient()
//                    .send(request, HttpResponse.BodyHandlers.ofString());
//
//            JsonNode json = mapper.readTree(response.body());
//            JsonNode vectorNode = json.get("data").get(0).get("embedding");
//
//            List<Double> vector = new ArrayList<>();
//            for (JsonNode val : vectorNode) {
//                vector.add(val.asDouble());
//            }
//
//            // Lưu vào cache với timestamp mới
//            embeddingCache.put(cacheKey, new CacheEntry(vector, System.currentTimeMillis()));
//
//            return vector;
//        } catch (Exception e) {
//            System.err.println("Lỗi khi lấy embedding: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    // Xóa cache đã hết hạn (có thể gọi bằng @Scheduled)
//    public void clearExpiredCache() {
//        embeddingCache.entrySet().removeIf(e -> e.getValue().isExpired());
//    }
//
//    public double cosineSimilarity(List<Double> v1, List<Double> v2) {
//        if (v1.size() != v2.size()) throw new IllegalArgumentException("Vector size mismatch");
//
//        double dot = 0, norm1 = 0, norm2 = 0;
//        for (int i = 0; i < v1.size(); i++) {
//            dot += v1.get(i) * v2.get(i);
//            norm1 += Math.pow(v1.get(i), 2);
//            norm2 += Math.pow(v2.get(i), 2);
//        }
//        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
//    }
//
//    public boolean isSimilar(String a, String b) {
//        try {
//            List<Double> embA = getEmbedding(a);
//            List<Double> embB = getEmbedding(b);
//            double similarity = cosineSimilarity(embA, embB);
//            return similarity >= 0.75;
//        } catch (Exception e) {
//            System.err.println("Lỗi khi so sánh embedding: " + e.getMessage());
//            return true;
//        }
//    }
//}


