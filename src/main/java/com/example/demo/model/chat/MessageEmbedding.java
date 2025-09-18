package com.example.demo.model.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "message_embeddings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEmbedding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    private ChatMessage chatMessage;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession chatSession;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_chunk_id")
    private TextChunk textChunk;
    
    @Column(name = "embedding_vector", columnDefinition = "vector(1536)")
    private double[] embeddingVector;
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "content_tsvector", columnDefinition = "tsvector", insertable = false, updatable = false)
    private String contentTsVector;
    
    // ✅ SỬA THÀNH JSONB TYPE
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Transient
    private Double similarityScore;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper method để chuyển từ List<Double> sang double[]
    public void setEmbeddingFromList(List<Double> embeddingList) {
        if (embeddingList != null) {
            this.embeddingVector = embeddingList.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        }
    }
    
    // Helper method để chuyển từ double[] sang List<Double>
    public List<Double> getEmbeddingAsList() {
        if (embeddingVector == null) return List.of();
        return Arrays.stream(embeddingVector)
            .boxed()
            .collect(Collectors.toList());
    }
    
    public String getChunkType() {
        if (metadata != null && metadata.containsKey("chunk_type")) {
            return metadata.get("chunk_type").toString();
        }
        return "unknown";
    }
    
    public Integer getTokenCount() {
        if (metadata != null && metadata.containsKey("token_count")) {
            Object value = metadata.get("token_count");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
    
    public Boolean hasOverlap() {
        if (metadata != null && metadata.containsKey("has_overlap")) {
            Object value = metadata.get("has_overlap");
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        }
        return false;
    }
    
    public boolean isHighlyRelevant() {
        return similarityScore != null && similarityScore > 0.7;
    }
    
    public boolean isModeratelyRelevant() {
        return similarityScore != null && similarityScore > 0.5;
    }
    
 // ✅ THÊM CÁC TRƯỜNG METADATA QUAN TRỌNG THÀNH COLUMN RIÊNG
    // ✅ SỬA ĐỘ DÀI CÁC TRƯỜNG CÓ THỂ VƯỢT QUÁ 255 KÝ TỰ
    @Column(name = "sender_type", length = 50)
    private String senderType; // "user" or "assistant"
    
    @Column(name = "message_timestamp")
    private LocalDateTime messageTimestamp;
    
    @Column(name = "detected_topic", length = 100)
    private String detectedTopic;
    
    // ✅ KEEP JSONB FOR ADDITIONAL METADATA
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> additionalMetadata;
    
    // ✅ HELPER METHODS FOR METADATA MANAGEMENT
    public void setStandardMetadata(String sender, LocalDateTime timestamp, String topic) {
        this.senderType = sender;
        this.messageTimestamp = timestamp;
        this.detectedTopic = topic;
        
        if (this.additionalMetadata == null) {
            this.additionalMetadata = new HashMap<>();
        }
    }
    
    public void setChunkMetadata(String chunkType, int tokenCount, boolean hasOverlap) {
        if (this.additionalMetadata == null) {
            this.additionalMetadata = new HashMap<>();
        }
        this.additionalMetadata.put(EmbeddingMetadata.CHUNK_TYPE, chunkType);
        this.additionalMetadata.put(EmbeddingMetadata.TOKEN_COUNT, tokenCount);
        this.additionalMetadata.put(EmbeddingMetadata.HAS_OVERLAP, hasOverlap);
    }
    
    // ✅ METHODS TO CHECK METADATA CONDITIONS
    public boolean isUserMessage() {
        return "user".equalsIgnoreCase(this.senderType);
    }
    
    public boolean isFromLastWeek() {
        if (this.messageTimestamp == null) return false;
        return this.messageTimestamp.isAfter(LocalDateTime.now().minusWeeks(1));
    }
    
    public boolean isAboutTopic(String topic) {
        return topic != null && topic.equalsIgnoreCase(this.detectedTopic);
    }
}