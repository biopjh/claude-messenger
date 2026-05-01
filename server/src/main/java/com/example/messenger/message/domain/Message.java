package com.example.messenger.message.domain;

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
public class Message {
    private Long id;
    private Long roomId;
    private Long senderId;
    private MessageType type;
    private String content;
    private OffsetDateTime createdAt;
}
