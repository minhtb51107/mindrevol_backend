-- Đầu tiên, xóa trigger cũ (từ V1) đang khóa cột 'text'
-- Nó không còn cần thiết cho kiến trúc LangChain4j mới
DROP TRIGGER IF EXISTS trigger_message_embedding_tsvector ON message_embeddings;

-- Bây giờ, việc thay đổi kiểu dữ liệu sẽ thành công
ALTER TABLE message_embeddings
ALTER COLUMN text TYPE TEXT;

-- Thay đổi cột metadata để tương thích JSONB
ALTER TABLE message_embeddings
ALTER COLUMN metadata TYPE JSONB USING metadata::jsonb;