package com.example.demo.repository.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface QuestionAnswerCacheRepository extends JpaRepository<QuestionAnswerCache, UUID> {

    /**
     * ✅ CẬP NHẬT QUERY Ở ĐÂY
     * Sửa đổi câu query để chọn ra các cột cụ thể và đặt alias cho chúng
     * khớp với tên trong QuestionAnswerCacheProjection.
     */
    @Query(nativeQuery = true, value =
        "SELECT " +
        "  id AS id, " + // Alias cho id
        "  answer_text AS answerText, " + // Alias cho answer_text
        "  question_embedding <=> CAST(:embedding AS vector) AS distance " +
        "FROM " +
        "  question_answer_cache " +
        "ORDER BY " +
        "  distance " +
        "LIMIT :limit"
    )
    List<QuestionAnswerCacheProjection> findNearestNeighborsWithDistance(
        @Param("embedding") String embedding,
        @Param("limit") int limit
    );
    
    /**
     * ✅ THÊM MỚI: Xóa tất cả các mục cache có chứa một documentId cụ thể
     * trong metadata.
     * Sử dụng toán tử @> của JSONB để kiểm tra xem mảng 'source_document_ids'
     * có chứa giá trị được cung cấp hay không.
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM question_answer_cache WHERE metadata -> 'source_document_ids' @> CAST(to_jsonb(CAST(:documentId AS text)) AS jsonb)")
    void deleteAllByDocumentId(@Param("documentId") String documentId);
}