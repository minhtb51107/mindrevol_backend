-- Gỡ bỏ các ràng buộc Foreign Key (Khóa ngoại) khỏi bảng message_embeddings
-- Lý do: Thư viện PgVectorEmbeddingStore của LangChain4j không biết cách điền các cột FK này;
-- nó chỉ điền vào các cột (embedding_id, embedding, text, metadata).
-- Toàn bộ thông tin (messageId, sessionId) đã được lưu an toàn trong cột metadata (JSONB).

-- 1. Gỡ bỏ ràng buộc FK của message_id
ALTER TABLE message_embeddings 
DROP CONSTRAINT IF EXISTS message_embeddings_message_id_fkey;

-- 2. Gỡ bỏ ràng buộc FK của session_id
ALTER TABLE message_embeddings 
DROP CONSTRAINT IF EXISTS message_embeddings_session_id_fkey;

-- 3. Cho phép các cột này được NULL (vì thư viện Java sẽ không điền chúng)
ALTER TABLE message_embeddings 
ALTER COLUMN message_id DROP NOT NULL;

ALTER TABLE message_embeddings 
ALTER COLUMN session_id DROP NOT NULL;