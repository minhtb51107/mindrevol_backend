-- Thêm cột pending_action nếu nó chưa tồn tại, với giá trị mặc định là 'NONE'
-- để xử lý các dòng dữ liệu cũ.
ALTER TABLE conversation_states
ADD COLUMN IF NOT EXISTS pending_action VARCHAR(255) NOT NULL DEFAULT 'NONE';

-- Thêm cột pending_action_context nếu nó chưa tồn tại.
ALTER TABLE conversation_states
ADD COLUMN IF NOT EXISTS pending_action_context JSONB;