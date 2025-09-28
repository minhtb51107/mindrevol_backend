package com.example.demo.repository.chat;

import com.example.demo.model.chat.QuestionAnswerCache;
// Bỏ import PGvector nếu nó không còn được dùng ở nơi khác trong file này
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionAnswerCacheRepository extends JpaRepository<QuestionAnswerCache, UUID> {

    // --- THAY ĐỔI CÂU QUERY VÀ CHỮ KÝ PHƯƠNG THỨC ---
    @Query(nativeQuery = true, value = """
        SELECT * FROM question_answer_cache
        WHERE (valid_until IS NULL OR valid_until > NOW())
        ORDER BY question_embedding <-> CAST(:embedding AS vector)
        LIMIT :limit
    """)
    List<QuestionAnswerCache> findNearestNeighbors(
            @Param("embedding") String embedding, // <-- THAY ĐỔI TỪ PGvector SANG String
            @Param("limit") int limit
    );
}