// src/main/java/com/example/demo/service/chat/preference/UserPreferenceService.java
package com.example.demo.service.chat.preference;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.UserPreference;
import com.example.demo.repository.auth.UserRepository; // THÊM IMPORT NÀY
import com.example.demo.repository.chat.UserPreferenceRepository.UserPreferenceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {
    
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository; // THÊM DEPENDENCY NÀY
    
    public UserPreference getOrCreateUserPreference(Long userId) {
        return userPreferenceRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultPreference(userId));
    }
    
    public void updateTopicInterest(Long userId, String topic, double delta) {
        UserPreference preference = getOrCreateUserPreference(userId);
        Map<String, Double> topics = preference.getFavoriteTopics();
        
        double currentInterest = topics.getOrDefault(topic, 0.5);
        double newInterest = Math.min(Math.max(currentInterest + delta, 0.0), 1.0);
        
        topics.put(topic, newInterest);
        userPreferenceRepository.save(preference);
    }
    
    public void detectAndUpdatePreferences(Long userId, String message, String aiResponse) {
        UserPreference preference = getOrCreateUserPreference(userId);
        
        // Detect communication style
        detectCommunicationStyle(preference, message);
        
        // Detect detail preference
        detectDetailPreference(preference, aiResponse);
        
        // Detect topics
        detectTopics(preference, message);
        
        userPreferenceRepository.save(preference);
    }
    
    private void detectCommunicationStyle(UserPreference preference, String message) {
        if (message.contains("please") || message.contains("would you") || message.contains("could you")) {
            preference.setCommunicationStyle("formal");
        } else if (message.contains("hey") || message.contains("what's up") || message.contains("lol")) {
            preference.setCommunicationStyle("casual");
        } else if (message.contains("explain") || message.contains("detail") || message.contains("how does")) {
            preference.setCommunicationStyle("technical");
        }
    }
    
    private void detectDetailPreference(UserPreference preference, String aiResponse) {
        if (aiResponse != null) {
            int length = aiResponse.length();
            if (length < 100) {
                preference.setDetailPreference("concise");
            } else if (length > 300) {
                preference.setDetailPreference("detailed");
            } else {
                preference.setDetailPreference("balanced");
            }
        }
    }
    
    private static final Map<String, Double> TOPIC_KEYWORDS = createTopicKeywords();
    
    private static Map<String, Double> createTopicKeywords() {
        Map<String, Double> map = new HashMap<>();
        map.put("technology", 0.1);
        map.put("programming", 0.2);
        map.put("code", 0.15);
        map.put("music", 0.1);
        map.put("song", 0.1);
        map.put("artist", 0.1);
        map.put("sports", 0.1);
        map.put("game", 0.1);
        map.put("player", 0.1);
        map.put("science", 0.1);
        map.put("research", 0.1);
        map.put("discover", 0.1);
        return Collections.unmodifiableMap(map);
    }
    
    private void detectTopics(UserPreference preference, String message) {
        
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            TOPIC_KEYWORDS.forEach((topic, value) -> {
                if (lowerMessage.contains(topic)) {
                    updateTopicInterest(preference.getUser().getId(), topic, value);
                }
            });
        }
    }
    
    private UserPreference createDefaultPreference(Long userId) {
        // ✅ SỬA LẠI: Lấy user từ database thay vì tạo mới
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        UserPreference preference = new UserPreference();
        preference.setUser(user); // ✅ Sử dụng user từ database
        preference.setFavoriteTopics(new HashMap<>());
        preference.setCommunicationStyle("balanced");
        preference.setDetailPreference("balanced");
        preference.setLearningStyle("visual");
        return userPreferenceRepository.save(preference);
    }
    
    // ✅ THÊM PHƯƠNG THỨC ĐỂ LẤY USER PREFERENCES CHO PROMPT BUILDING
    public Map<String, Object> getUserPreferencesForPrompt(Long userId) {
        UserPreference preference = getOrCreateUserPreference(userId);
        Map<String, Object> prefs = new HashMap<>();
        
        prefs.put("communicationStyle", preference.getCommunicationStyle());
        prefs.put("detailPreference", preference.getDetailPreference());
        prefs.put("learningStyle", preference.getLearningStyle());
        prefs.put("favoriteTopics", preference.getFavoriteTopics());
        
        return prefs;
    }
}