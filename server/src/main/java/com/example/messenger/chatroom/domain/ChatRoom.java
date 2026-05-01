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
public class ChatRoom {
    private Long id;
    private ChatRoomType type;
    private String title;
    private Long createdBy;
    private OffsetDateTime createdAt;
}
