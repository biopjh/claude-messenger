package com.example.messenger.chatroom.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMember {
    private Long id;
    private Long roomId;
    private Long userId;
    private OffsetDateTime joinedAt;
    private Long lastReadMessageId;
}
