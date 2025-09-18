package com.example.demo.service.chat.memory;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.service.chat.ChatAIService;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisChatMemoryService {
    private static final String USER_QUESTIONS_KEY = "chat:user:questions:";
    private static final String AI_ANSWERS_KEY = "chat:ai:answers:";
    private static final long TTL_HOURS = 24; // Giữ dữ liệu 24 giờ

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisChatMemoryService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Lưu câu hỏi của user
    public void saveUserQuestion(Long sessionId, String question) {
        // ✅ KIỂM TRA TRÙNG LẶP TRONG REDIS
        List<String> recentQuestions = getUserQuestions(sessionId);
        if (!recentQuestions.isEmpty()) {
            String lastQuestion = recentQuestions.get(recentQuestions.size() - 1);
            if (lastQuestion.equals(question)) {
                log.warn("⚠️ Duplicate question in Redis: {}", question);
                return; // Không lưu nếu trùng với câu hỏi cuối cùng
            }
        }
        
        // Lưu vào Redis
        String key = "session:" + sessionId + ":user_questions";
        redisTemplate.opsForList().leftPush(key, question);
        
        // ✅ GIỚI HẠN SỐ LƯỢNG QUESTION TRONG REDIS
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 20) {
            redisTemplate.opsForList().trim(key, 0, 19);
        }
        
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
    }

    // Lưu câu trả lời của AI
    public void saveAIAnswer(Long sessionId, String answer) {
        String key = AI_ANSWERS_KEY + sessionId;
        redisTemplate.opsForList().rightPush(key, answer);
        redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
    }

    // Lấy danh sách câu hỏi của user
    public List<String> getUserQuestions(Long sessionId) {
        String key = USER_QUESTIONS_KEY + sessionId;
        List<Object> objects = redisTemplate.opsForList().range(key, 0, -1);
        return objects != null ? objects.stream()
                .map(obj -> (String) obj)
                .toList() : new ArrayList<>();
    }

    // Lấy danh sách câu trả lời của AI
    public List<String> getAIAnswers(Long sessionId) {
        String key = AI_ANSWERS_KEY + sessionId;
        List<Object> objects = redisTemplate.opsForList().range(key, 0, -1);
        return objects != null ? objects.stream()
                .map(obj -> (String) obj)
                .toList() : new ArrayList<>();
    }

    // Giới hạn số lượng cặp Q&A (tối đa 10 cặp)
    public void trimConversation(Long sessionId, int maxPairs) {
        String userKey = USER_QUESTIONS_KEY + sessionId;
        String aiKey = AI_ANSWERS_KEY + sessionId;

        long userSize = redisTemplate.opsForList().size(userKey);
        long aiSize = redisTemplate.opsForList().size(aiKey);

        if (userSize > maxPairs) {
            redisTemplate.opsForList().trim(userKey, userSize - maxPairs, userSize);
        }
        if (aiSize > maxPairs) {
            redisTemplate.opsForList().trim(aiKey, aiSize - maxPairs, aiSize);
        }
    }

    // Xóa dữ liệu của session
    public void clearSessionData(Long sessionId) {
        String userKey = USER_QUESTIONS_KEY + sessionId;
        String aiKey = AI_ANSWERS_KEY + sessionId;
        redisTemplate.delete(userKey);
        redisTemplate.delete(aiKey);
    }
}