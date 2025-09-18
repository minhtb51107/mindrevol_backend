package com.example.demo.service.chat.memory.langchain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangChainChatMemoryService {

    // ✅ SỬA LỖI: Chúng ta cần RedisTemplate (thay vì ChatMemoryStore)
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper; // Đảm bảo bạn đã tiêm ObjectMapper bean

    private static final String REDIS_PREFIX = "lc4j_chat_memory:";
    private static final int MAX_MESSAGES = 20; // Cửa sổ bộ nhớ

    // Cache các đối tượng memory để tránh tạo lại
    private final Map<Long, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getChatMemory(Long sessionId) {
        return memoryCache.computeIfAbsent(sessionId, id -> 
            new RedisListChatMemory(id, redisTemplate, objectMapper)
        );
    }

    /**
     * ✅ LỚP INNER CLASS ĐƯỢC TỐI ƯU HÓA HOÀN TOÀN
     * Triển khai ChatMemory sử dụng Redis LIST thay vì Value (SET)
     */
    @RequiredArgsConstructor
    private static class RedisListChatMemory implements ChatMemory {

        private final String key;
        private final ListOperations<String, String> listOps;
        private final ObjectMapper objectMapper;

        public RedisListChatMemory(Long sessionId, RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
            this.key = REDIS_PREFIX + sessionId;
            this.listOps = redisTemplate.opsForList();
            this.objectMapper = objectMapper;
        }

        @Override
        public String id() {
            return key;
        }

        /**
         * ✅ TỐI ƯU HÓA: Đọc trực tiếp từ Redis List.
         */
        @Override
        public List<ChatMessage> messages() {
            try {
                List<String> jsonMessages = listOps.range(key, 0, MAX_MESSAGES - 1);
                if (jsonMessages == null || jsonMessages.isEmpty()) {
                    return new ArrayList<>();
                }
                
                // Tin nhắn Redis lưu từ mới nhất -> cũ nhất (vì LPUSH), 
                // chúng ta cần đảo ngược lại
                return jsonMessages.stream()
                        .map(this::deserialize)
                        .collect(Collectors.collectingAndThen(
                            Collectors.toList(), 
                            list -> {
                                java.util.Collections.reverse(list);
                                return list;
                            }
                        ));
            } catch (Exception e) {
                log.warn("Không thể tải chat memory từ Redis: {}", e.getMessage());
                return new ArrayList<>();
            }
        }

        /**
         * ✅ TỐI ƯU HÓA: Chỉ PUSH tin nhắn mới và TRIM danh sách.
         */
        @Override
        public void add(ChatMessage message) {
            try {
                String jsonMessage = serialize(message);
                listOps.leftPush(key, jsonMessage); // LPUSH (thêm vào đầu)
                listOps.trim(key, 0, MAX_MESSAGES - 1); // TRIM (giữ lại 20)
            } catch (Exception e) {
                log.warn("Không thể lưu chat memory vào Redis: {}", e.getMessage());
            }
        }

        @Override
        public void clear() {
            listOps.getOperations().delete(key);
        }

        // --- Các phương thức Serialization/Deserialization Helper ---
        // (Đây là phần phức tạp vì ChatMessage là một interface)

        @SneakyThrows(JsonProcessingException.class)
        private String serialize(ChatMessage message) {
            String type;
            if (message instanceof AiMessage) {
                type = "ai";
            } else if (message instanceof UserMessage) {
                type = "user";
            } else if (message instanceof SystemMessage) {
                type = "system";
            } else {
                throw new IllegalArgumentException("Loại tin nhắn không xác định: " + message.getClass());
            }
            // Lưu dưới dạng một map đơn giản
            return objectMapper.writeValueAsString(Map.of(
                "type", type, 
                "content", message.text()
            ));
        }

        @SneakyThrows
        private ChatMessage deserialize(String json) {
            Map<String, String> map = objectMapper.readValue(json, Map.class);
            String type = map.get("type");
            String content = map.get("content");

            switch (type) {
                case "ai":
                    return AiMessage.from(content);
                case "user":
                    return UserMessage.from(content);
                case "system":
                    return SystemMessage.from(content);
                default:
                    throw new IllegalArgumentException("Không thể deserialize loại tin nhắn: " + type);
            }
        }
    }
}