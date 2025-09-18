-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create message_embeddings table
CREATE TABLE IF NOT EXISTS message_embeddings (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT REFERENCES chat_message(id) ON DELETE CASCADE,
    session_id BIGINT REFERENCES chat_session(id) ON DELETE CASCADE,
    embedding_vector vector(1536),
    content TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create index for vector similarity search
CREATE INDEX IF NOT EXISTS message_embeddings_session_idx 
ON message_embeddings USING ivfflat (embedding_vector vector_cosine_ops)
WITH (lists = 100)
WHERE session_id IS NOT NULL;

-- Create index for session filtering
CREATE INDEX IF NOT EXISTS message_embeddings_session_id_idx 
ON message_embeddings(session_id);

-- Thêm cột text_chunk_id và foreign key constraint
ALTER TABLE message_embeddings 
ADD COLUMN text_chunk_id BIGINT;

-- Thêm foreign key constraint
ALTER TABLE message_embeddings 
ADD CONSTRAINT fk_message_embeddings_text_chunk 
FOREIGN KEY (text_chunk_id) REFERENCES text_chunks(id) ON DELETE CASCADE;

-- Cập nhật comment cho cột
COMMENT ON COLUMN message_embeddings.text_chunk_id IS 'Reference to text_chunks table for chunk-based embeddings';

-- Thêm cột metadata với kiểu jsonb
ALTER TABLE message_embeddings 
ADD COLUMN IF NOT EXISTS metadata JSONB;

-- Cập nhật comment cho cột
COMMENT ON COLUMN message_embeddings.metadata IS 'Metadata for chunk information (type, token count, overlap, etc.)';

CREATE INDEX IF NOT EXISTS message_embeddings_session_hnsw_idx 
ON message_embeddings USING hnsw (embedding_vector vector_cosine_ops)
WITH (m = 16, ef_construction = 64)
WHERE session_id IS NOT NULL AND text_chunk_id IS NOT NULL;

-- Composite index cho filtering hiệu quả
CREATE INDEX IF NOT EXISTS message_embeddings_session_chunk_idx 
ON message_embeddings(session_id, text_chunk_id);

-- Index cho metadata filtering
CREATE INDEX IF NOT EXISTS message_embeddings_metadata_idx 
ON message_embeddings USING gin (
    (metadata->'chunk_type'),
    (metadata->'token_count'),
    (metadata->'has_overlap')
);

-- Index cho full-text search trên chunk content
CREATE INDEX IF NOT EXISTS message_embeddings_chunk_tsvector_idx 
ON message_embeddings USING GIN(content_tsvector)
WHERE text_chunk_id IS NOT NULL;

-- Thêm các column metadata quan trọng
ALTER TABLE message_embeddings 
ADD COLUMN IF NOT EXISTS sender_type VARCHAR(20),
ADD COLUMN IF NOT EXISTS message_timestamp TIMESTAMP,
ADD COLUMN IF NOT EXISTS detected_topic VARCHAR(100);

-- Thêm index cho các column metadata thường dùng
CREATE INDEX IF NOT EXISTS idx_message_embeddings_sender 
ON message_embeddings(sender_type) 
WHERE session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_embeddings_timestamp 
ON message_embeddings(message_timestamp) 
WHERE session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_embeddings_topic 
ON message_embeddings(detected_topic) 
WHERE session_id IS NOT NULL;

-- Composite index cho query phổ biến
CREATE INDEX IF NOT EXISTS idx_message_embeddings_sender_topic_time 
ON message_embeddings(session_id, sender_type, detected_topic, message_timestamp);