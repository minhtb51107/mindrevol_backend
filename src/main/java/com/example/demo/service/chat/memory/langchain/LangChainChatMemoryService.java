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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${application.chat-memory.max-messages:20}")
    private int maxMessages;

    @Value("${application.chat-memory.summary-trigger-count:15}")
    private int summaryTriggerCount;

    @Value("${application.chat-memory.messages-to-summarize:10}")
    private int messagesToSummarize;


    private static final String REDIS_MESSAGES_PREFIX = "lc4j_chat_messages:";
    private static final String REDIS_SUMMARY_PREFIX = "lc4j_chat_summary:";

    private final Map<Long, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    public ChatMemory getChatMemory(Long sessionId) {
        return memoryCache.computeIfAbsent(sessionId, id ->
                new SummarizingRedisChatMemory(
                        id,
                        this.redisTemplate,
                        this.objectMapper,
                        this.conversationSummaryService,
                        this.memoryCache,
                        maxMessages,
                        summaryTriggerCount,
                        messagesToSummarize
                )
        );
    }

    /**
     * LỚP INNER CLASS ĐÃ ĐƯỢC SỬA LỖI VÀ CẬP NHẬT
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
        private final RedisTemplate<String, String> redisTemplate;
        private final Map<Long, ChatMemory> memoryCache;

        private final int maxMessages;
        private final int summaryTriggerCount;
        private final int messagesToSummarize;


        public SummarizingRedisChatMemory(
                Long sessionId,
                RedisTemplate<String, String> redisTemplate,
                ObjectMapper objectMapper,
                ConversationSummaryService summaryService,
                Map<Long, ChatMemory> memoryCache,
                int maxMessages,
                int summaryTriggerCount,
                int messagesToSummarize
        ) {
            this.sessionId = sessionId;
            this.messagesKey = REDIS_MESSAGES_PREFIX + sessionId;
            this.summaryKey = REDIS_SUMMARY_PREFIX + sessionId;
            this.redisTemplate = redisTemplate;
            this.memoryCache = memoryCache;
            this.listOps = redisTemplate.opsForList();
            this.valueOps = redisTemplate.opsForValue();
            this.objectMapper = objectMapper;
            this.summaryService = summaryService;
            this.maxMessages = maxMessages;
            this.summaryTriggerCount = summaryTriggerCount;
            this.messagesToSummarize = messagesToSummarize;
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
                listOps.trim(messagesKey, 0, this.maxMessages - 1);

                Long currentMessageCount = listOps.size(messagesKey);
                if (currentMessageCount != null && currentMessageCount >= this.summaryTriggerCount) {
                    summarizeAndPrune();
                }
            } catch (Exception e) {
                log.error("Không thể lưu chat memory vào Redis cho session {}: {}", sessionId, e.getMessage(), e);
            }
        }

        private void summarizeAndPrune() {
            log.info("Kích hoạt tóm tắt cho session ID: {}", sessionId);

            Long currentMessageCount = listOps.size(messagesKey);
            // Sử dụng this. để chỉ rõ là đang dùng field của class
            if (currentMessageCount == null || currentMessageCount < this.messagesToSummarize) {
                return;
            }

            // Sử dụng this. để chỉ rõ là đang dùng field của class
            List<String> jsonMessagesToSummarize = listOps.range(messagesKey, (long) -this.messagesToSummarize, -1L);
            if (jsonMessagesToSummarize == null || jsonMessagesToSummarize.isEmpty()) {
                return;
            }

            // ✅ SỬA LỖI 1: Đổi tên biến local để tránh che khuất (shadowing) field của class
            List<ChatMessage> messagesForSummary = jsonMessagesToSummarize.stream()
                    .map(this::deserialize)
                    .collect(Collectors.toList());
            Collections.reverse(messagesForSummary);

            String existingSummaryJson = valueOps.get(summaryKey);
            if (existingSummaryJson != null) {
                messagesForSummary.add(0, deserialize(existingSummaryJson));
            }

            String newSummaryContent = summaryService.generateSummary(messagesForSummary);
            SystemMessage newSummaryMessage = SystemMessage.from("Đây là tóm tắt cuộc trò chuyện trước đó: " + newSummaryContent);

            valueOps.set(summaryKey, serialize(newSummaryMessage));

            // ✅ SỬA LỖI 2: Ép kiểu tường minh và sử dụng `this.` để đảm bảo tính toán chính xác
            long messagesToKeep = currentMessageCount.longValue() - this.messagesToSummarize;
            if (messagesToKeep > 0) {
                listOps.trim(messagesKey, 0, messagesToKeep - 1);
            } else {
                redisTemplate.delete(messagesKey);
            }

            log.info("Hoàn thành tóm tắt và làm gọn bộ nhớ cho session ID: {}", sessionId);
        }

        @Override
        public void clear() {
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