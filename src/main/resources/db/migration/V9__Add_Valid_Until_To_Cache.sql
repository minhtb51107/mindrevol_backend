-- V9__Add_Valid_Until_To_Cache.sql
ALTER TABLE question_answer_cache
ADD COLUMN valid_until TIMESTAMPTZ;

-- Optional: Create an index for faster lookups on valid_until
CREATE INDEX IF NOT EXISTS idx_question_answer_cache_valid_until
ON question_answer_cache (valid_until);