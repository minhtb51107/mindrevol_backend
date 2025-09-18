//package com.example.demo.model;
//
//import java.time.LocalDateTime;
//
//import jakarta.persistence.Column;
//import jakarta.persistence.Entity;
//import jakarta.persistence.FetchType;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//import jakarta.persistence.JoinColumn;
//import jakarta.persistence.ManyToOne;
//
//import lombok.Data;
//
//@Entity
//@Data // ✅ Sinh toàn bộ getter/setter/toString/hashCode/equals
//public class Message {
//    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private String role; // user / assistant
//
//    @Column(columnDefinition = "TEXT")
//    private String content;
//
//    private LocalDateTime createdAt = LocalDateTime.now();
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "session_id")
//    private ChatSession session;
//}


