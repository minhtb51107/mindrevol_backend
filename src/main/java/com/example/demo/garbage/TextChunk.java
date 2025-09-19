//package com.example.demo.model.chat;
//
//import jakarta.persistence.*;
//import lombok.*;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity
//@Table(name = "text_chunks")
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//public class TextChunk {
//    
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "message_id")
//    private ChatMessage originalMessage;
//    
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "session_id")
//    private ChatSession chatSession;
//    
//    @Column(columnDefinition = "TEXT")
//    private String content;
//    
//    private int chunkIndex;
//    private int totalChunks;
//    
//    @Column(name = "chunk_type")
//    private String chunkType; // "sentence", "paragraph", "fixed_size"
//    
//    private int tokenCount;
//    private boolean hasOverlap;
//    
//    @Column(name = "created_at")
//    private LocalDateTime createdAt;
//    
//    // ✅ THÊM TRƯỜNG DETECTED TOPIC
//    @Column(name = "detected_topic", length = 100)
//    private String detectedTopic;
//    
//    @OneToMany(mappedBy = "textChunk", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<MessageEmbedding> messageEmbeddings = new ArrayList<>();
//}
package com.example.demo.garbage;

