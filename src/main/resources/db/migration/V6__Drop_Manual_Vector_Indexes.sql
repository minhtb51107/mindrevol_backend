-- Xóa các vector index đã được tạo thủ công trong V1 và V2
-- để giao toàn quyền quản lý (tạo và truy vấn) index cho thư viện PgVectorEmbeddingStore (Java).

DROP INDEX IF EXISTS message_embeddings_session_ivfflat_idx;
DROP INDEX IF EXISTS message_embeddings_session_hnsw_idx;