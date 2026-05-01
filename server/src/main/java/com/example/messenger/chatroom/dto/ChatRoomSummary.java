package com.example.messenger.chatroom.dto;

import com.example.messenger.chatroom.domain.ChatRoomType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 채팅방 목록 한 줄. 마지막 메시지·안읽음수까지 한 번에 가져온다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomSummary {
    private Long roomId;
    private ChatRoomType roomType;
    private String displayTitle;            // GROUP 은 title, DIRECT 는 상대 닉네임
    private String displayProfileImageUrl;  // DIRECT 만 채워짐(상대 프로필). GROUP 은 null
    private Long lastMessageId;
    private String lastMessagePreview;       // 미리보기 100자
    private OffsetDateTime lastMessageAt;
    private long unreadCount;
}
