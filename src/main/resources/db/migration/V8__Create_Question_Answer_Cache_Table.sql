-- V8__Create_Question_Answer_Cache_Table.sql

-- Bảng này sẽ lưu trữ các câu hỏi đã được hỏi và câu trả lời tương ứng
CREATE TABLE IF NOT EXISTS question_answer_cache (
    id UUID PRIMARY KEY,
    question_text TEXT NOT NULL,
    answer_text TEXT NOT NULL,
    question_embedding VECTOR(1536) NOT NULL, -- Thay 1536 bằng số chiều embedding của bạn
    metadata JSONB,
    access_count INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Tạo index trên cột embedding để tăng tốc độ tìm kiếm tương đồng
-- Sử dụng HNSW cho hiệu suất cao. Thay đổi các tham số nếu cần.
CREATE INDEX IF NOT EXISTS idx_hnsw_question_embedding
ON question_answer_cache
USING hnsw (question_embedding vector_cosine_ops);

-- Index trên last_accessed_at để tối ưu cho việc dọn dẹp cache (LRU)
CREATE INDEX IF NOT EXISTS idx_cache_last_accessed
ON question_answer_cache (last_accessed_at);