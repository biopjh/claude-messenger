package com.example.messenger.friend.dto;

import com.example.messenger.friend.domain.FriendshipStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 친구 목록/요청 목록 화면에 한 줄로 보여주기 위한 join 결과.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FriendListItem {
    private Long friendshipId;       // friendships.id
    private FriendshipStatus status;
    private Long userId;             // 상대방 users.id
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String statusMessage;
}
