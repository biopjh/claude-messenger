-- ============================================================
-- 메시지 본문 부분일치 검색용 trigram 인덱스
--   - 한국어/영어 모두 ILIKE '%keyword%' 가 인덱스로 빠르게 동작하도록
--   - 삭제된 메시지는 검색 대상에서 제외 → partial index 로 공간/속도 절약
--   - SYSTEM 메시지는 type 으로 거르므로 인덱스에는 포함해도 무방
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_messages_content_trgm
    ON messages USING GIN (content gin_trgm_ops)
    WHERE deleted_at IS NULL;
