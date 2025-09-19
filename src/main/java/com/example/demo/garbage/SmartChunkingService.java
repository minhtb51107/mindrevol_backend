//package com.example.demo.service.chat.chunking;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.model.chat.TextChunk;
//import com.example.demo.repository.chat.TextChunkRepository;
//import dev.langchain4j.model.chat.ChatLanguageModel;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class SmartChunkingService {
//
//    private final TextChunkRepository chunkRepository;
//    private final TokenCounterService tokenCounterService;
//    private final ChatLanguageModel chatLanguageModel;
//    
//    private static final double AVG_CHARS_PER_TOKEN = 3.5;
//    private static final int DEFAULT_CHUNK_SIZE = 500; // tokens
//    private static final int DEFAULT_OVERLAP = 50; // tokens
//    private static final int CHUNKING_THRESHOLD = 200; // tokens
//
//    @Transactional
//    public List<TextChunk> chunkMessageIfNeeded(ChatMessage message) {
//        int tokenCount = tokenCounterService.countTokens(message.getContent());
//
//        // Chỉ chunk nếu vượt quá ngưỡng
//        if (tokenCount <= CHUNKING_THRESHOLD) {
//            return List.of(createSingleChunk(message, tokenCount));
//        }
//
//        // Adaptive chunking strategy
//        if (isCodeContent(message.getContent())) {
//            return chunkByCodeStructure(message, tokenCount);
//        } else if (hasParagraphStructure(message.getContent())) {
//            return chunkByParagraphs(message, tokenCount);
//        } else {
//            return chunkByFixedSize(message, tokenCount);
//        }
//    }
//
//    private TextChunk createSingleChunk(ChatMessage message, int tokenCount) {
//        String topic = detectTopic(message.getContent());
//        
//        return TextChunk.builder()
//            .originalMessage(message)
//            .chatSession(message.getChatSession())
//            .content(message.getContent())
//            .chunkIndex(0)
//            .totalChunks(1)
//            .chunkType("single")
//            .tokenCount(tokenCount)
//            .hasOverlap(false)
//            .createdAt(LocalDateTime.now())
//            .detectedTopic(topic)
//            .build();
//    }
//
//    private List<TextChunk> chunkByFixedSize(ChatMessage message, int tokenCount) {
//        List<TextChunk> chunks = new ArrayList<>();
//        String content = message.getContent();
//        int start = 0;
//        int chunkIndex = 0;
//
//        while (start < content.length()) {
//            int end = findOptimalSplitPoint(content, start, DEFAULT_CHUNK_SIZE);
//            String chunkContent = content.substring(start, end);
//            String topic = detectTopic(chunkContent);
//
//            chunks.add(TextChunk.builder()
//                .originalMessage(message)
//                .chatSession(message.getChatSession())
//                .content(chunkContent)
//                .chunkIndex(chunkIndex)
//                .totalChunks(-1) // Will be updated later
//                .chunkType("fixed_size")
//                .tokenCount(tokenCounterService.countTokens(chunkContent))
//                .hasOverlap(chunkIndex > 0)
//                .createdAt(LocalDateTime.now())
//                .detectedTopic(topic)
//                .build());
//
//            // Overlap logic
//            start = chunkIndex > 0 ? end - DEFAULT_OVERLAP : end;
//            chunkIndex++;
//        }
//
//        // Update total chunks
//        chunks.forEach(chunk -> chunk.setTotalChunks(chunks.size()));
//        return chunks;
//    }
//
//    private List<TextChunk> chunkByParagraphs(ChatMessage message, int tokenCount) {
//        String[] paragraphs = message.getContent().split("\n\n");
//        List<TextChunk> chunks = new ArrayList<>();
//
//        for (int i = 0; i < paragraphs.length; i++) {
//            int paraTokenCount = tokenCounterService.countTokens(paragraphs[i]);
//            String topic = detectTopic(paragraphs[i]);
//
//            if (paraTokenCount > DEFAULT_CHUNK_SIZE) {
//                // Paragraph quá dài, chunk tiếp
//                chunks.addAll(chunkByFixedSizeForContent(paragraphs[i], message, i, "paragraph_split"));
//            } else {
//                chunks.add(TextChunk.builder()
//                    .originalMessage(message)
//                    .chatSession(message.getChatSession())
//                    .content(paragraphs[i])
//                    .chunkIndex(i)
//                    .totalChunks(paragraphs.length)
//                    .chunkType("paragraph")
//                    .tokenCount(paraTokenCount)
//                    .hasOverlap(false)
//                    .createdAt(LocalDateTime.now())
//                    .detectedTopic(topic)
//                    .build());
//            }
//        }
//
//        return chunks;
//    }
//
//    private List<TextChunk> chunkByCodeStructure(ChatMessage message, int tokenCount) {
//        String content = message.getContent();
//        
//        // Try to split by common code patterns
//        String[] codeSections = content.split("(?=\\b(public|private|protected|class|interface|function|def)\\b)");
//        
//        if (codeSections.length > 1) {
//            List<TextChunk> chunks = new ArrayList<>();
//            
//            for (int i = 0; i < codeSections.length; i++) {
//                String section = codeSections[i].trim();
//                if (!section.isEmpty()) {
//                    int sectionTokenCount = tokenCounterService.countTokens(section);
//                    String topic = detectTopic(section);
//                    
//                    if (sectionTokenCount > DEFAULT_CHUNK_SIZE * 1.5) {
//                        // Section is still too long, use fixed size chunking
//                        chunks.addAll(chunkByFixedSizeForContent(section, message, i, "code_large"));
//                    } else {
//                        chunks.add(TextChunk.builder()
//                            .originalMessage(message)
//                            .chatSession(message.getChatSession())
//                            .content(section)
//                            .chunkIndex(i)
//                            .totalChunks(codeSections.length)
//                            .chunkType("code_section")
//                            .tokenCount(sectionTokenCount)
//                            .hasOverlap(false)
//                            .createdAt(LocalDateTime.now())
//                            .detectedTopic(topic)
//                            .build());
//                    }
//                }
//            }
//            
//            return chunks;
//        }
//        
//        // Fallback to fixed size chunking
//        return chunkByFixedSize(message, tokenCount);
//    }
//
//    private List<TextChunk> chunkByFixedSizeForContent(String content, ChatMessage originalMessage, int baseIndex,
//                                                     String chunkType) {
//        List<TextChunk> chunks = new ArrayList<>();
//        int start = 0;
//        int chunkIndex = 0;
//
//        while (start < content.length()) {
//            int end = findOptimalSplitPoint(content, start, DEFAULT_CHUNK_SIZE);
//            String chunkContent = content.substring(start, Math.min(end, content.length()));
//            String topic = detectTopic(chunkContent);
//
//            chunks.add(TextChunk.builder()
//                .originalMessage(originalMessage)
//                .chatSession(originalMessage.getChatSession())
//                .content(chunkContent)
//                .chunkIndex(baseIndex + chunkIndex)
//                .totalChunks(-1) // Will be updated later
//                .chunkType(chunkType)
//                .tokenCount(tokenCounterService.countTokens(chunkContent))
//                .hasOverlap(chunkIndex > 0)
//                .createdAt(LocalDateTime.now())
//                .detectedTopic(topic)
//                .build());
//
//            // Overlap logic - move start position with overlap for next chunk
//            if (chunkIndex > 0 && end < content.length()) {
//                // Find the overlap start point (go back by overlap tokens)
//                int overlapStart = findOverlapStartPoint(content, end, DEFAULT_OVERLAP);
//                start = Math.max(overlapStart, start + 1); // Ensure we make progress
//            } else {
//                start = end;
//            }
//
//            chunkIndex++;
//        }
//
//        // Update total chunks for all chunks in this group
//        final int totalChunks = chunks.size();
//        chunks.forEach(chunk -> chunk.setTotalChunks(totalChunks));
//
//        return chunks;
//    }
//
//    // ✅ TOPIC DETECTION METHOD
//    public String detectTopic(String content) {
//        if (content == null || content.length() < 10) {
//            return "general";
//        }
//        
//        try {
//            // Use a simple rule-based approach first for performance
//            String lowerContent = content.toLowerCase();
//            
//            if (lowerContent.contains("java") || lowerContent.contains("spring") || 
//                lowerContent.contains("program") || lowerContent.contains("code")) {
//                return "programming";
//            }
//            
//            if (lowerContent.contains("weather") || lowerContent.contains("rain") || 
//                lowerContent.contains("temperature")) {
//                return "weather";
//            }
//            
//            if (lowerContent.contains("music") || lowerContent.contains("song") || 
//                lowerContent.contains("artist")) {
//                return "music";
//            }
//            
//            // For longer content, use AI detection
//            if (content.length() > 50) {
//                return detectTopicWithAI(content);
//            }
//            
//            return "general";
//        } catch (Exception e) {
//            log.warn("Topic detection failed: {}", e.getMessage());
//            return "general";
//        }
//    }
//    
//    // ✅ AI-BASED TOPIC DETECTION (WITH FALLBACK)
//    private String detectTopicWithAI(String content) {
//        try {
//            String systemPrompt = "Phân tích đoạn văn và trả về 1 từ khóa chủ đề duy nhất. " +
//                    "Chỉ trả về từ khóa, không giải thích. " +
//                    "Các chủ đề phổ biến: programming, weather, music, sports, food, general.";
//            
//            String userPrompt = "Xác định chủ đề cho đoạn văn sau: " + 
//                    content.substring(0, Math.min(200, content.length()));
//            
//            String fullPrompt = systemPrompt + "\n\n" + userPrompt;
//            String topic = chatLanguageModel.generate(fullPrompt);
//            
//            return topic != null ? topic.trim().toLowerCase() : "general";
//        } catch (Exception e) {
//            log.warn("AI topic detection failed, using fallback: {}", e.getMessage());
//            return "general";
//        }
//    }
//
//    // Các phương thức helper khác giữ nguyên...
//    private boolean isCodeContent(String content) {
//        return content.contains("public class") || content.contains("function") || content.contains("def ")
//                || content.contains("import ");
//    }
//
//    private boolean hasParagraphStructure(String content) {
//        return content.split("\n\n").length > 1;
//    }
//
//    private int findOverlapStartPoint(String content, int currentEnd, int overlapTokens) {
//        // Find where to start the overlap (go backwards by approximately overlapTokens)
//        int targetChars = (int) (overlapTokens * AVG_CHARS_PER_TOKEN);
//        int start = Math.max(0, currentEnd - targetChars);
//
//        // Try to find a good break point (space, punctuation, etc.)
//        for (int i = start; i < currentEnd; i++) {
//            if (i <= 0)
//                return 0;
//
//            char c = content.charAt(i);
//            if (Character.isWhitespace(c) || c == '.' || c == '!' || c == '?') {
//                return i + 1; // Start after the break character
//            }
//        }
//
//        return start;
//    }
//
//    private int findOptimalSplitPoint(String content, int start, int targetTokenCount) {
//        if (start >= content.length()) {
//            return content.length();
//        }
//
//        // Calculate target character position
//        int targetChars = (int) (targetTokenCount * AVG_CHARS_PER_TOKEN);
//        int potentialEnd = Math.min(start + targetChars, content.length());
//
//        // If we're at the end, return it
//        if (potentialEnd >= content.length()) {
//            return content.length();
//        }
//
//        // Look for a good break point near the target position
//        return findNearestBreakPoint(content, potentialEnd);
//    }
//
//    private int findNearestBreakPoint(String content, int position) {
//        // Look backwards for a break point
//        for (int i = position; i > Math.max(0, position - 100); i--) {
//            if (i >= content.length()) {
//                return content.length();
//            }
//
//            char c = content.charAt(i);
//            if (isGoodBreakPoint(c)) {
//                return i + 1; // Include the break character
//            }
//        }
//
//        // Look forwards for a break point
//        for (int i = position; i < Math.min(content.length(), position + 100); i++) {
//            char c = content.charAt(i);
//            if (isGoodBreakPoint(c)) {
//                return i + 1;
//            }
//        }
//
//        // Fallback: split at the original position
//        return position;
//    }
//
//    private boolean isGoodBreakPoint(char c) {
//        return Character.isWhitespace(c) || c == '.' || c == '!' || c == '?' || c == ';' || c == ',' || c == ':'
//                || c == '\n' || c == '\r';
//    }
//}

