package com.example.messenger.friend.domain;

public enum FriendshipStatus {
    PENDING,    // 요청만 보낸 상태 (상대 수락 대기)
    ACCEPTED,   // 양방향으로 친구
    BLOCKED     // 차단
}
