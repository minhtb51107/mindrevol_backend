//package com.example.demo.service.chat.util;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//import org.springframework.stereotype.Service;
//
//import com.example.demo.service.chat.chunking.TokenCounterService;
//
//
//import lombok.RequiredArgsConstructor;
//
////src/main/java/com/example/service/chat/util/TokenManagementService.java
//@Service
//@RequiredArgsConstructor
//public class TokenManagementService {
// 
// private final TokenCounterService tokenCounterService;
// private static final int MAX_TOKENS = 4000; // GPT-4o context window
// private static final int SAFETY_BUFFER = 200; // Buffer an toàn
// 
// public boolean willExceedTokenLimit(List<Map<String, String>> messages, int maxTokens) {
//     int totalTokens = calculateTotalTokens(messages);
//     return totalTokens > (maxTokens - SAFETY_BUFFER);
// }
// 
// public int calculateTotalTokens(List<Map<String, String>> messages) {
//     int total = 0;
//     for (Map<String, String> message : messages) {
//         total += tokenCounterService.countTokens(message.get("content"));
//         total += 4; // Ước tính token cho role và formatting
//     }
//     return total;
// }
// 
// public List<Map<String, String>> truncateMessages(List<Map<String, String>> messages, int maxTokens) {
//     List<Map<String, String>> truncated = new ArrayList<>();
//     int currentTokens = 0;
//     
//     // Luôn giữ system message và user query
//     Map<String, String> systemMessage = messages.get(0);
//     Map<String, String> userQuery = messages.get(messages.size() - 1);
//     
//     truncated.add(systemMessage);
//     currentTokens += tokenCounterService.countTokens(systemMessage.get("content")) + 4;
//     
//     // Thêm các message từ cuối lên (ưu tiên message gần nhất)
//     for (int i = messages.size() - 2; i > 0; i--) {
//         Map<String, String> message = messages.get(i);
//         int messageTokens = tokenCounterService.countTokens(message.get("content")) + 4;
//         
//         if (currentTokens + messageTokens + 
//             tokenCounterService.countTokens(userQuery.get("content")) + 4 > maxTokens - SAFETY_BUFFER) {
//             break;
//         }
//         
//         truncated.add(1, message); // Thêm vào sau system message
//         currentTokens += messageTokens;
//     }
//     
//     truncated.add(userQuery);
//     return truncated;
// }
//}


