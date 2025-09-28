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

    // ✅ THAY ĐỔI LỚN:
    // 1. Thêm cột 'distance' vào SELECT.
    // 2. Thay đổi kiểu trả về thành List<QuestionAnswerCacheProjection>.
    // 3. Đổi tên phương thức để phản ánh việc nó trả về cả distance.
    @Query(nativeQuery = true, value = """
        SELECT 
            id, question_text, answer_text, question_embedding, metadata, 
            access_count, created_at, last_accessed_at, valid_until,
            question_embedding <-> CAST(:embedding AS vector) AS distance
        FROM question_answer_cache
        WHERE (valid_until IS NULL OR valid_until > NOW())
        ORDER BY distance
        LIMIT :limit
    """)
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