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

    /** 수정 시각. null 이면 한 번도 수정되지 않은 원본. */
    private OffsetDateTime editedAt;
    /** 소프트 삭제 시각. null 이면 정상. non-null 이면 "삭제된 메시지" 로 표시. */
    private OffsetDateTime deletedAt;
    /** 답장 대상 메시지 id. null 이면 일반 메시지. */
    private Long replyToMessageId;
}
