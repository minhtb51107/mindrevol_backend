//package com.example.demo.service.chat;
//
//import java.util.concurrent.CompletableFuture;
//
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import com.example.demo.model.chat.ConversationState;
//import com.example.demo.model.chat.EmotionContext;
//import com.example.demo.model.chat.UserPreference;
//import com.example.demo.repository.chat.ChatMessageRepository;
//import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
//import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
//import com.example.demo.repository.chat.UserPreferenceRepository.UserPreferenceRepository;
//
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//
//@Service
//@RequiredArgsConstructor
//@Transactional
//public class BatchService {
//    
//    private final EmotionContextRepository emotionContextRepository;
//    private final ConversationStateRepository conversationStateRepository;
//    private final UserPreferenceRepository userPreferenceRepository;
//    private final ChatMessageRepository chatMessageRepository;
//
//    public void saveAllContext(EmotionContext emotionContext, 
//                             ConversationState conversationState,
//                             UserPreference userPreference) {
//        if (emotionContext != null) {
//            emotionContextRepository.save(emotionContext);
//        }
//        if (conversationState != null) {
//            conversationStateRepository.save(conversationState);
//        }
//        if (userPreference != null) {
//            userPreferenceRepository.save(userPreference);
//        }
//    }
//
//    @Async
//    public CompletableFuture<Void> saveAllAsync(EmotionContext emotionContext,
//                                              ConversationState conversationState,
//                                              UserPreference userPreference) {
//        saveAllContext(emotionContext, conversationState, userPreference);
//        return CompletableFuture.completedFuture(null);
//    }
//}