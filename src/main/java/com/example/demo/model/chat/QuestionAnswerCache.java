package com.example.demo.model.chat;

import com.pgvector.PGvector;
import com.example.demo.config.database.PGvectorUserType; // ✅ 1. IMPORT CLASS MỚI
import jakarta.persistence.*;
import lombok.Data;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes; // ✅ 2. THÊM IMPORT


import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "question_answer_cache")
public class QuestionAnswerCache {

    @Id
    private UUID id;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    // ✅ 2. SỬ DỤNG PGvectorType
    // ✅ 2. SỬ DỤNG PGvectorUserType CỦA BẠN
    @Type(PGvectorUserType.class)
    @Column(name = "question_embedding", nullable = false, columnDefinition = "vector(1536)")
    private PGvector questionEmbedding;


 // ✅ 3. THÊM ANNOTATION @JdbcTypeCode CHO CỘT METADATA
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "access_count")
    private Integer accessCount = 1;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "last_accessed_at")
    private ZonedDateTime lastAccessedAt = ZonedDateTime.now();

    public QuestionAnswerCache() {
    }
}