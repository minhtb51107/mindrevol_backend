-- Xóa Khóa chính (Primary Key) cũ (đang nằm trên cột "id" của V1)
-- VÀ Thêm Khóa chính mới vào cột "embedding_id" (cột UUID mà LangChain4j yêu cầu)

ALTER TABLE message_embeddings
    DROP CONSTRAINT IF EXISTS message_embeddings_pkey,
    ADD PRIMARY KEY (embedding_id);