package com.example.demo.repository.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionAnswerCacheRepository extends JpaRepository<QuestionAnswerCache, UUID> {

    /**
     * Tìm kiếm câu hỏi tương tự nhất bằng khoảng cách cosine.
     * 1 - (embedding <=> :embedding) tính toán cosine similarity.
     * @param embedding Vector embedding của câu hỏi cần tìm.
     * @return Danh sách các kết quả, mỗi kết quả là một mảng Object chứa [id, answerText, similarity].
     */
    @Query(value = """
        SELECT id, answer_text, 1 - (question_embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM question_answer_cache
        ORDER BY similarity DESC
        LIMIT 1
        """, nativeQuery = true)
    List<Object[]> findMostSimilarQuestion(@Param("embedding") String embedding);
}