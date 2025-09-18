-- Kích hoạt pgcrypto (nếu chưa có) để tạo UUID
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- GỘP 3 BƯỚC VÀO 1 LỆNH: 
-- 1. Xóa Identity (nếu tồn tại)
-- 2. Đảm bảo kiểu là UUID (bỏ qua mệnh đề USING gây lỗi, vì cột có thể đã là UUID)
-- 3. Đặt giá trị DEFAULT
ALTER TABLE message_embeddings
    ALTER COLUMN embedding_id DROP IDENTITY IF EXISTS,
    ALTER COLUMN embedding_id TYPE UUID,
    ALTER COLUMN embedding_id SET DEFAULT gen_random_uuid();