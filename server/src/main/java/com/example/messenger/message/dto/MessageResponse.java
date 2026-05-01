package com.example.messenger.message.dto;

import com.example.messenger.message.domain.Message;
import com.example.messenger.message.domain.MessageType;

import java.time.OffsetDateTime;

/**
 * 화면용 메시지 DTO. 첨부가 있으면 attachmentUrl 등이 채워진다(LEFT JOIN attachments).
 *  - TEXT/SYSTEM: 첨부 필드는 모두 null
 *  - IMAGE/FILE : 첨부 필드 채워짐. content 는 원본 파일명(표시용)
 *  senderProfileImageUrl 은 채팅 메시지 옆 아바타 표시에 사용된다.
 */
public record MessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String senderNickname,
        String senderProfileImageUrl,
        MessageType type,
        String content,
        OffsetDateTime createdAt,
        String attachmentUrl,
        String attachmentMimeType,
        Long attachmentSizeBytes
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(), m.getRoomId(), m.getSenderId(),
                null, null, m.getType(), m.getContent(), m.getCreatedAt(),
                null, null, null);
    }
}
