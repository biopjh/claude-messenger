-- ============================================================
-- 카카오톡 클론 메신저 - 초기 스키마
-- 이번 마이그레이션에서는 회원/친구/채팅방/메시지/첨부 모든 테이블을 함께 생성합니다.
-- (이번 단계는 users 만 사용하지만, 나중에 차근차근 코드를 붙일 때 마이그레이션이 한 번 더
--  돌지 않게 미리 만들어 둡니다.)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500),
    status_message    VARCHAR(200),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS friendships (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','BLOCKED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, friend_id)
);
CREATE INDEX IF NOT EXISTS idx_friendships_user ON friendships(user_id, status);

CREATE TABLE IF NOT EXISTS chat_rooms (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(10) NOT NULL CHECK (type IN ('DIRECT','GROUP')),
    title       VARCHAR(100),
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_room_members (
    id                    BIGSERIAL PRIMARY KEY,
    room_id               BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id               BIGINT NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    joined_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_message_id  BIGINT,
    UNIQUE (room_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_room_members_user ON chat_room_members(user_id);

CREATE TABLE IF NOT EXISTS messages (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id   BIGINT NOT NULL REFERENCES users(id),
    type        VARCHAR(10) NOT NULL DEFAULT 'TEXT'
                CHECK (type IN ('TEXT','IMAGE','FILE','SYSTEM')),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_room_created ON messages(room_id, created_at DESC);

CREATE TABLE IF NOT EXISTS attachments (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    mime_type   VARCHAR(100),
    size_bytes  BIGINT
);
