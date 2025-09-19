//package com.example.demo.model.chat;
//
//import jakarta.persistence.*;
//import lombok.*;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity
//@Table(name = "hierarchical_memory")
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//public class HierarchicalMemory {
//    
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//    
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "chat_session_id", nullable = false)
//    private ChatSession chatSession;
//    
//    @Column(name = "hierarchy_level")
//    private int hierarchyLevel; // 0: leaf, 1: summary of 10 segments, 2: summary of 100 segments, etc.
//    
//    @Column(name = "segment_start")
//    private int segmentStart;
//    
//    @Column(name = "segment_end")
//    private int segmentEnd;
//    
//    @Column(columnDefinition = "TEXT")
//    private String summaryContent;
//    
//    @Column(name = "created_at")
//    private LocalDateTime createdAt;
//    
//    @Column(name = "updated_at")
//    private LocalDateTime updatedAt;
//    
//    // Reference to child summaries (for building hierarchy)
//    @OneToMany(mappedBy = "parentSummary", cascade = CascadeType.ALL)
//    private List<HierarchicalMemory> childSummaries = new ArrayList<>();
//    
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "parent_summary_id")
//    private HierarchicalMemory parentSummary;
//    
//    @PrePersist
//    protected void onCreate() {
//        createdAt = LocalDateTime.now();
//        updatedAt = LocalDateTime.now();
//    }
//    
//    @PreUpdate
//    protected void onUpdate() {
//        updatedAt = LocalDateTime.now();
//    }
//}