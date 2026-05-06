-- ============================================================
-- 메시지 수정 / 삭제 / 답장(인용) 지원
-- ============================================================

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS edited_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT
        REFERENCES messages(id) ON DELETE SET NULL;

-- 답장 트리 추적용 (각 메시지의 답글 빠르게 찾기)
CREATE INDEX IF NOT EXISTS idx_messages_reply_to ON messages(reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;
