package com.example.demo.model.chat;

public class EmbeddingMetadata {
    public static final String SENDER = "sender";
    public static final String TIMESTAMP = "timestamp";
    public static final String TOPIC = "topic";
    public static final String CHUNK_TYPE = "chunk_type";
    public static final String TOKEN_COUNT = "token_count";
    public static final String HAS_OVERLAP = "has_overlap";
    public static final String MESSAGE_TYPE = "message_type"; // "user", "assistant"
    
    private EmbeddingMetadata() {} // Utility class
}