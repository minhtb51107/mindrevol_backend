package com.example.demo.repository.chat;

import com.example.demo.model.chat.MessageEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageEmbeddingRepository extends JpaRepository<MessageEmbedding, Long> {

	// Tìm embedding bằng messageId
	Optional<MessageEmbedding> findByChatMessage_Id(Long messageId);

	// Tìm tất cả embeddings của một session
	List<MessageEmbedding> findByChatSession_Id(Long sessionId);
	
	// Tìm embedding bằng textChunkId
	Optional<MessageEmbedding> findByTextChunkId(Long textChunkId);

	// Xóa embedding bằng messageId
	void deleteByChatMessage_Id(Long messageId);
	
	@Modifying
    @Query("DELETE FROM MessageEmbedding me WHERE me.chatSession.id = :sessionId")
    void deleteByChatSessionId(@Param("sessionId") Long sessionId);
    
    // Thêm phương thức xóa theo textChunkId
    @Modifying
    @Query("DELETE FROM MessageEmbedding me WHERE me.textChunk.id = :textChunkId")
    void deleteByTextChunkId(@Param("textChunkId") Long textChunkId);

	@Query(value = """
			SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
			FROM message_embeddings me
			WHERE me.session_id = :sessionId
			ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findSimilarMessages(@Param("embedding") String embedding, // ✅ Thay đổi từ List<Double> sang String
			@Param("sessionId") Long sessionId, @Param("limit") int limit);

	@Query(value = """
			SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
			FROM message_embeddings me
			WHERE me.session_id = :sessionId
			AND 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) > :similarityThreshold
			ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findSimilarMessagesWithThreshold(@Param("embedding") String embedding, // ✅ Thay đổi từ List<Double>
																							// sang String
			@Param("sessionId") Long sessionId, @Param("similarityThreshold") double similarityThreshold,
			@Param("limit") int limit);

	// Đếm số lượng embeddings của một session
	long countByChatSession_Id(Long sessionId);

	// Kiểm tra xem message đã có embedding chưa
	boolean existsByChatMessage_Id(Long messageId);

	@Query(value = "SELECT me FROM MessageEmbedding me WHERE me.chatSession.id = :sessionId")
	List<MessageEmbedding> findAllBySessionId(@Param("sessionId") Long sessionId);
	
	// BM25 search using PostgreSQL full-text search
    @Query(value = """
        SELECT me, ts_rank(me.content_tsvector, to_tsquery('simple', :query)) as score
        FROM message_embeddings me
        WHERE me.session_id = :sessionId
        AND me.content_tsvector @@ to_tsquery('simple', :query)
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findByKeywordSearch(@Param("query") String query, 
                                     @Param("sessionId") Long sessionId, 
                                     @Param("limit") int limit);
    
    // Hybrid search combining BM25 and vector search
    @Query(value = """
        WITH semantic_search AS (
            SELECT me.id, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            ORDER BY similarity DESC
            LIMIT :limit * 2
        ),
        keyword_search AS (
            SELECT me.id, ts_rank(me.content_tsvector, to_tsquery('simple', :query)) as score
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.content_tsvector @@ to_tsquery('simple', :query)
            ORDER BY score DESC
            LIMIT :limit * 2
        ),
        combined_results AS (
            SELECT 
                COALESCE(s.id, k.id) as id,
                COALESCE(s.similarity, 0) as semantic_score,
                COALESCE(k.score, 0) as keyword_score,
                (COALESCE(s.similarity, 0) * :semanticWeight + 
                 COALESCE(k.score, 0) * :keywordWeight) as combined_score
            FROM semantic_search s
            FULL OUTER JOIN keyword_search k ON s.id = k.id
            ORDER BY combined_score DESC
            LIMIT :limit
        )
        SELECT me.*, cr.combined_score as similarity
        FROM combined_results cr
        JOIN message_embeddings me ON cr.id = me.id
        ORDER BY cr.combined_score DESC
        """, nativeQuery = true)
    List<Object[]> findHybridResults(@Param("embedding") String embedding,
                                   @Param("query") String query,
                                   @Param("sessionId") Long sessionId,
                                   @Param("semanticWeight") double semanticWeight,
                                   @Param("keywordWeight") double keywordWeight,
                                   @Param("limit") int limit);
    
    @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.text_chunk_id IS NOT NULL
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarChunks(@Param("embedding") String embedding,
                                       @Param("sessionId") Long sessionId, 
                                       @Param("limit") int limit);
        
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.text_chunk_id IS NOT NULL
            AND 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) > :similarityThreshold
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarChunksWithThreshold(@Param("embedding") String embedding,
                                                    @Param("sessionId") Long sessionId, 
                                                    @Param("similarityThreshold") double similarityThreshold,
                                                    @Param("limit") int limit);
        
     // ✅ PRE-FILTERING QUERIES WITH METADATA
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.sender_type = :senderType
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarMessagesBySender(
            @Param("embedding") String embedding,
            @Param("sessionId") Long sessionId,
            @Param("senderType") String senderType,
            @Param("limit") int limit);
        
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.message_timestamp >= :startTime
            AND me.message_timestamp <= :endTime
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarMessagesByTimeRange(
            @Param("embedding") String embedding,
            @Param("sessionId") Long sessionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);
        
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND LOWER(me.detected_topic) LIKE LOWER(CONCAT('%', :topic, '%'))
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarMessagesByTopic(
            @Param("embedding") String embedding,
            @Param("sessionId") Long sessionId,
            @Param("topic") String topic,
            @Param("limit") int limit);
        
        // ✅ COMBINED FILTERING EXAMPLE
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.sender_type = :senderType
            AND me.message_timestamp >= :startTime
            AND LOWER(me.detected_topic) LIKE LOWER(CONCAT('%', :topic, '%'))
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarMessagesWithMultipleFilters(
            @Param("embedding") String embedding,
            @Param("sessionId") Long sessionId,
            @Param("senderType") String senderType,
            @Param("startTime") LocalDateTime startTime,
            @Param("topic") String topic,
            @Param("limit") int limit);
        
        // ✅ JSONB METADATA FILTERING
        @Query(value = """
            SELECT me, 1 - (me.embedding_vector <=> CAST(:embedding AS vector)) as similarity
            FROM message_embeddings me
            WHERE me.session_id = :sessionId
            AND me.additional_metadata->>'chunk_type' = :chunkType
            ORDER BY me.embedding_vector <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
        List<Object[]> findSimilarMessagesByChunkType(
            @Param("embedding") String embedding,
            @Param("sessionId") Long sessionId,
            @Param("chunkType") String chunkType,
            @Param("limit") int limit);
}