package com.example.demo.service.chat.memory.langchain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LangChainChatMemoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConversationSummaryService conversationSummaryService;

    private static final String REDIS_MESSAGES_PREFIX = "lc4j_chat_messages:";
    private static final String REDIS_SUMMARY_PREFIX = "lc4j_chat_summary:";
    private static final int MAX_MESSAGES = 20;
    private static final int SUMMARY_TRIGGER_COUNT = 15;
    private static final int MESSAGES_TO_SUMMARIZE = 10;

    private final Map<Long, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getChatMemory(Long sessionId) {
        return memoryCache.computeIfAbsent(sessionId, id ->
                // ✅ SỬA LỖI: Truyền redisTemplate và memoryCache vào constructor
                new SummarizingRedisChatMemory(
                        id,
                        this.redisTemplate, // Sửa ở đây
                        this.objectMapper,
                        this.conversationSummaryService,
                        this.memoryCache // Sửa ở đây
                )
        );
    }

    /**
     * LỚP INNER CLASS ĐÃ ĐƯỢC SỬA LỖI HOÀN CHỈNH
     */
    @RequiredArgsConstructor
    private static class SummarizingRedisChatMemory implements ChatMemory {

        private final Long sessionId;
        private final String messagesKey;
        private final String summaryKey;
        private final ListOperations<String, String> listOps;
        private final ValueOperations<String, String> valueOps;
        private final ObjectMapper objectMapper;
        private final ConversationSummaryService summaryService;
        // ✅ SỬA LỖI: Thêm các trường để lớp inner có thể truy cập
        private final RedisTemplate<String, String> redisTemplate;
        private final Map<Long, ChatMemory> memoryCache;


        public SummarizingRedisChatMemory(
                Long sessionId,
                RedisTemplate<String, String> redisTemplate,
                ObjectMapper objectMapper,
                ConversationSummaryService summaryService,
                Map<Long, ChatMemory> memoryCache // Thêm vào constructor
        ) {
            this.sessionId = sessionId;
            this.messagesKey = REDIS_MESSAGES_PREFIX + sessionId;
            this.summaryKey = REDIS_SUMMARY_PREFIX + sessionId;
            // ✅ SỬA LỖI: Gán các đối tượng được truyền vào
            this.redisTemplate = redisTemplate;
            this.memoryCache = memoryCache;
            this.listOps = redisTemplate.opsForList();
            this.valueOps = redisTemplate.opsForValue();
            this.objectMapper = objectMapper;
            this.summaryService = summaryService;
        }

        @Override
        public String id() {
            return String.valueOf(sessionId);
        }

        @Override
        public List<ChatMessage> messages() {
            try {
                String summaryJson = valueOps.get(summaryKey);
                List<ChatMessage> chatMessages = new ArrayList<>();
                if (summaryJson != null && !summaryJson.isEmpty()) {
                    chatMessages.add(deserialize(summaryJson));
                }

                List<String> jsonMessages = listOps.range(messagesKey, 0, -1);
                if (jsonMessages == null || jsonMessages.isEmpty()) {
                    return chatMessages;
                }

                List<ChatMessage> recentMessages = jsonMessages.stream()
                        .map(this::deserialize)
                        .collect(Collectors.toList());
                Collections.reverse(recentMessages);
                chatMessages.addAll(recentMessages);

                return chatMessages;
            } catch (Exception e) {
                log.warn("Không thể tải chat memory từ Redis cho session {}: {}", sessionId, e.getMessage());
                return new ArrayList<>();
            }
        }

        @Override
        public void add(ChatMessage message) {
            try {
                String jsonMessage = serialize(message);
                listOps.leftPush(messagesKey, jsonMessage);
                listOps.trim(messagesKey, 0, MAX_MESSAGES - 1);

                Long currentMessageCount = listOps.size(messagesKey);
                if (currentMessageCount != null && currentMessageCount >= SUMMARY_TRIGGER_COUNT) {
                    summarizeAndPrune();
                }
            } catch (Exception e) {
                log.error("Không thể lưu chat memory vào Redis cho session {}: {}", sessionId, e.getMessage(), e);
            }
        }

        private void summarizeAndPrune() {
            log.info("Kích hoạt tóm tắt cho session ID: {}", sessionId);

            // ✅ SỬA LỖI: Lấy kích thước danh sách hiện tại một cách an toàn
            Long currentMessageCount = listOps.size(messagesKey);
            if (currentMessageCount == null || currentMessageCount < MESSAGES_TO_SUMMARIZE) {
                return; // Không đủ tin nhắn để tóm tắt
            }

            List<String> jsonMessagesToSummarize = listOps.range(messagesKey, -MESSAGES_TO_SUMMARIZE, -1);
            if (jsonMessagesToSummarize == null || jsonMessagesToSummarize.isEmpty()) {
                return;
            }

            List<ChatMessage> messagesToSummarize = jsonMessagesToSummarize.stream()
                    .map(this::deserialize)
                    .collect(Collectors.toList());
            Collections.reverse(messagesToSummarize);

            String existingSummaryJson = valueOps.get(summaryKey);
            if (existingSummaryJson != null) {
                messagesToSummarize.add(0, deserialize(existingSummaryJson));
            }

            String newSummaryContent = summaryService.generateSummary(messagesToSummarize);
            SystemMessage newSummaryMessage = SystemMessage.from("Đây là tóm tắt cuộc trò chuyện trước đó: " + newSummaryContent);

            valueOps.set(summaryKey, serialize(newSummaryMessage));

            // ✅ SỬA LỖI: Sử dụng currentMessageCount đã được xác định
            listOps.trim(messagesKey, 0, currentMessageCount - MESSAGES_TO_SUMMARIZE - 1);
            log.info("Hoàn thành tóm tắt và làm gọn bộ nhớ cho session ID: {}", sessionId);
        }

        @Override
        public void clear() {
            // ✅ SỬA LỖI: Sử dụng redisTemplate và memoryCache đã được truyền vào
            redisTemplate.delete(messagesKey);
            redisTemplate.delete(summaryKey);
            memoryCache.remove(sessionId);
        }

        // --- Các phương thức Serialization/Deserialization Helper (Giữ nguyên) ---
        @SneakyThrows(JsonProcessingException.class)
        private String serialize(ChatMessage message) {
            String type;
            Object content;
            if (message instanceof AiMessage) {
                type = "ai";
                content = message.text();
            } else if (message instanceof UserMessage) {
                type = "user";
                content = message.text();
            } else if (message instanceof SystemMessage) {
                type = "system";
                content = message.text();
            } else {
                throw new IllegalArgumentException("Loại tin nhắn không xác định: " + message.getClass());
            }

            return objectMapper.writeValueAsString(Map.of("type", type, "content", content));
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