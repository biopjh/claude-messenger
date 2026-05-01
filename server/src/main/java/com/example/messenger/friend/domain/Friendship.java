package com.example.messenger.friend.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * friendships 테이블 매핑.
 * 한 행은 user_id → friend_id 방향의 관계 1건. 양방향 관계가 필요하면 두 행을 만든다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {
    private Long id;
    private Long userId;
    private Long friendId;
    private FriendshipStatus status;
    private OffsetDateTime createdAt;
}
