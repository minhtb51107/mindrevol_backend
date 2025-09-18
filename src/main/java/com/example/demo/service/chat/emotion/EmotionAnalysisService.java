// src/main/java/com/example/demo/service/chat/emotion/EmotionAnalysisService.java
package com.example.demo.service.chat.emotion;

import dev.langchain4j.model.chat.ChatLanguageModel;
import com.example.demo.model.chat.EmotionContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmotionAnalysisService {
    
    private final ChatLanguageModel chatLanguageModel;
    
    // Thay thế Map.of() bằng HashMap truyền thống
    private static final Map<String, Double> EMOTION_KEYWORDS = createEmotionKeywords();

    private static Map<String, Double> createEmotionKeywords() {
        Map<String, Double> map = new HashMap<>();
        map.put("happy", 0.8);
        map.put("joy", 0.9);
        map.put("excited", 0.85);
        map.put("great", 0.7);
        map.put("sad", 0.8);
        map.put("unhappy", 0.75);
        map.put("disappoint", 0.8);
        map.put("sorry", 0.7);
        map.put("angry", 0.9);
        map.put("mad", 0.85);
        map.put("frustrat", 0.8);
        map.put("annoy", 0.75);
        map.put("neutral", 0.5);
        map.put("ok", 0.4);
        map.put("fine", 0.4);
        return Collections.unmodifiableMap(map);
    }
    
    // Emotion patterns với regex
    private static final Map<Pattern, String> EMOTION_PATTERNS = createEmotionPatterns();

    private static Map<Pattern, String> createEmotionPatterns() {
        Map<Pattern, String> patterns = new HashMap<>();
        patterns.put(Pattern.compile("(?i)(very|really|extremely|so) (happy|excited|joyful)"), "excited");
        patterns.put(Pattern.compile("(?i)(not good|not happy|unhappy|sad)"), "sad");
        patterns.put(Pattern.compile("(?i)(angry|mad|furious|pissed)"), "angry");
        patterns.put(Pattern.compile("(?i)(\\!\\!|\\!{2,})"), "excited");
        patterns.put(Pattern.compile("(?i)(\\?\\?|\\?{2,})"), "confused");
        return Collections.unmodifiableMap(patterns);
    }
    
    public EmotionContext analyzeEmotion(String text, EmotionContext existingContext) {
        EmotionContext context = existingContext != null ? existingContext : new EmotionContext();
        
        // Rule-based analysis
        String detectedEmotion = detectEmotionFromText(text);
        double intensity = calculateEmotionIntensity(text, detectedEmotion);
        
        // AI-based analysis for complex cases
        if (intensity > 0.7 || isComplexEmotion(text)) {
            Map<String, Object> aiAnalysis = analyzeWithAI(text);
            detectedEmotion = (String) aiAnalysis.getOrDefault("emotion", detectedEmotion);
            intensity = (Double) aiAnalysis.getOrDefault("intensity", intensity);
        }
        
        context.setCurrentEmotion(detectedEmotion);
        context.setEmotionIntensity(intensity);
        
        // Đảm bảo không bao giờ trả về null
        if (context.getEmotionIntensity() == null) {
            context.setEmotionIntensity(0.5); // Giá trị mặc định
        }
        
        return context;
    }
    
    private String detectEmotionFromText(String text) {
        String lowerText = text.toLowerCase();
        
        // Check patterns first
        for (Map.Entry<Pattern, String> entry : EMOTION_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(lowerText).find()) {
                return entry.getValue();
            }
        }
        
        // Then check keywords
        String dominantEmotion = "neutral";
        double maxScore = 0.0;
        
        for (Map.Entry<String, Double> entry : EMOTION_KEYWORDS.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                double score = entry.getValue() * countOccurrences(lowerText, entry.getKey());
                if (score > maxScore) {
                    maxScore = score;
                    dominantEmotion = entry.getKey();
                }
            }
        }
        
        return dominantEmotion;
    }
    
    private double calculateEmotionIntensity(String text, String emotion) {
        double baseIntensity = EMOTION_KEYWORDS.getOrDefault(emotion, 0.5);
        
        // Adjust based on text features
        int exclamationCount = countOccurrences(text, "!");
        int questionCount = countOccurrences(text, "?");
        int length = text.length();
        
        double intensity = baseIntensity;
        intensity += exclamationCount * 0.1;
        intensity -= questionCount * 0.05;
        intensity += Math.min(length / 100.0, 0.3);
        
        return Math.min(Math.max(intensity, 0.1), 1.0);
    }
    
    private Map<String, Object> analyzeWithAI(String text) {
        try {
            String prompt = "Phân tích cảm xúc của đoạn text sau. Trả về JSON format: {\"emotion\": \"\", \"intensity\": 0.0, \"confidence\": 0.0}\n\nText: " + text;
            
            String response = chatLanguageModel.generate(prompt);
            
            // Parse JSON response (simplified)
            return Map.of(
                "emotion", extractValue(response, "emotion", "neutral"),
                "intensity", Double.parseDouble(extractValue(response, "intensity", "0.5")),
                "confidence", Double.parseDouble(extractValue(response, "confidence", "0.7"))
            );
            
        } catch (Exception e) {
            // Sử dụng HashMap thay vì Map.of() để tránh lỗi với null values
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("emotion", "neutral");
            fallback.put("intensity", 0.5);
            fallback.put("confidence", 0.5);
            return fallback;
        }
    }
    
    private int countOccurrences(String text, String substring) {
        return text.length() - text.replace(substring, "").length();
    }
    
    private boolean isComplexEmotion(String text) {
        return text.length() > 50 && countOccurrences(text, " ") > 8;
    }
    
    private String extractValue(String json, String key, String defaultValue) {
        // Simple JSON extraction
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"?([^,\"}]+)\"?");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).replace("\"", "") : defaultValue;
    }
}