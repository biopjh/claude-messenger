-- ============================================================
-- 메시지 이모지 리액션 (👍 ❤️ 😂 😮 😢 🔥)
--   - 한 (사용자, 메시지) 쌍은 같은 이모지로 단 1번만 반응 가능 (UNIQUE)
--   - 다른 이모지로는 동시 반응 가능 (👍 + ❤️ 동시)
--   - 같은 이모지 반복 클릭 = toggle (서비스 레이어 책임)
-- ============================================================

CREATE TABLE IF NOT EXISTS message_reactions (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT      NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    emoji       VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (message_id, user_id, emoji)
);

CREATE INDEX IF NOT EXISTS idx_message_reactions_message ON message_reactions(message_id);
