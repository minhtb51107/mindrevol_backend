//package com.example.demo.service.chat.chunking;
//
//import org.springframework.stereotype.Service;
//
//import java.util.regex.Pattern;
//
//@Service
//public class TokenCounterService {
//    
//    // Approximate token counting based on OpenAI's approach
//    // ~4 characters per token for English, ~2-3 for Vietnamese
//    private static final double AVG_CHARS_PER_TOKEN = 3.5;
//    private static final Pattern WORD_PATTERN = Pattern.compile("\\s+");
//    private static final Pattern CJK_PATTERN = Pattern.compile("[" +
//        "\\u4E00-\\u9FFF" + // Chinese characters
//        "\\u3040-\\u309F" + // Hiragana
//        "\\u30A0-\\u30FF" + // Katakana  
//        "\\uAC00-\\uD7AF" + // Hangul
//        "]");
//    
//    public int countTokens(String text) {
//        if (text == null || text.isEmpty()) {
//            return 0;
//        }
//        
//        // Simple approximation for Vietnamese/English text
//        if (containsCJK(text)) {
//            // For CJK languages, each character is roughly a token
//            return text.length();
//        }
//        
//        // For Vietnamese/English, use word-based approximation
//        int wordCount = countWords(text);
//        return (int) Math.ceil(wordCount * 1.33); // ~1.33 tokens per word
//    }
//    
//    public int countWords(String text) {
//        if (text == null || text.trim().isEmpty()) {
//            return 0;
//        }
//        
//        String trimmed = text.trim();
//        if (trimmed.isEmpty()) {
//            return 0;
//        }
//        
//        return WORD_PATTERN.split(trimmed).length;
//    }
//    
//    public int countCharsPerTokenApproximation(String text) {
//        if (text == null || text.isEmpty()) {
//            return 0;
//        }
//        
//        return (int) Math.ceil(text.length() / AVG_CHARS_PER_TOKEN);
//    }
//    
//    private boolean containsCJK(String text) {
//        return CJK_PATTERN.matcher(text).find();
//    }
//    
//    public boolean isLikelyCodeContent(String text) {
//        if (text == null) return false;
//        
//        String[] codeIndicators = {
//            "public", "class", "function", "def ", "import ", "package ",
//            "var ", "let ", "const ", "{}", "();", "=>", "==", "!="
//        };
//        
//        for (String indicator : codeIndicators) {
//            if (text.contains(indicator)) {
//                return true;
//            }
//        }
//        
//        return false;
//    }
//}

