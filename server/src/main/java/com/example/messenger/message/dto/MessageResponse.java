package com.example.messenger.message.dto;

import com.example.messenger.message.domain.Message;
import com.example.messenger.message.domain.MessageType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 화면용 메시지 DTO. (LEFT JOIN attachments + reply 메시지 + reactions)
 *
 *  유형 정리:
 *   - TEXT/SYSTEM: attachment 필드 모두 null
 *   - IMAGE/FILE : attachment 채워짐, content 는 표시용 원본 파일명
 *   - editedAt 있음 → "(편집됨)" 표시
 *   - deletedAt 있음 → "삭제된 메시지" 표시 (content 무시)
 *   - replyToMessageId 있음 → 메시지 위에 인용 박스
 *     replyToContent==null && replyToMessageId!=null → 인용 대상이 삭제됨 ("삭제된 메시지")
 *   - reactions: 이모지별 집계. userIds 안에 me.id 가 있으면 "내가 반응한 칩" 으로 강조.
 *
 *  18 필드 record 지만, MyBatis 가 SQL 로 채울 수 있는 17 필드용 보조 생성자를 따로 둔다.
 *  service 레이어가 reactions 를 따로 fetch 해서 withReactions(...) 로 결합한다.
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
        Long attachmentSizeBytes,

        OffsetDateTime editedAt,
        OffsetDateTime deletedAt,

        Long replyToMessageId,
        String replyToSenderNickname,
        String replyToContent,
        MessageType replyToType,

        List<ReactionSummary> reactions
) {

    /** Canonical 보강 — null 이면 빈 리스트로 정규화. */
    public MessageResponse {
        if (reactions == null) reactions = List.of();
    }

    /**
     * MyBatis 가 호출할 17-arg 보조 생성자.
     * resultMap 의 &lt;arg&gt; 가 17개라 이쪽이 매칭된다. reactions 는 빈 리스트로 시작하고,
     * 서비스 레이어가 withReactions(...) 로 채운다.
     */
    public MessageResponse(
            Long id, Long roomId, Long senderId, String senderNickname,
            String senderProfileImageUrl, MessageType type, String content, OffsetDateTime createdAt,
            String attachmentUrl, String attachmentMimeType, Long attachmentSizeBytes,
            OffsetDateTime editedAt, OffsetDateTime deletedAt,
            Long replyToMessageId, String replyToSenderNickname,
            String replyToContent, MessageType replyToType
    ) {
        this(id, roomId, senderId, senderNickname,
             senderProfileImageUrl, type, content, createdAt,
             attachmentUrl, attachmentMimeType, attachmentSizeBytes,
             editedAt, deletedAt,
             replyToMessageId, replyToSenderNickname, replyToContent, replyToType,
             List.of());
    }

    /** Domain → DTO 변환. SYSTEM 메시지를 보낼 때 사용. reactions 는 빈 리스트. */
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(), m.getRoomId(), m.getSenderId(),
                null, null,
                m.getType(), m.getContent(), m.getCreatedAt(),
                null, null, null,
                m.getEditedAt(), m.getDeletedAt(),
                m.getReplyToMessageId(), null, null, null
        );
    }

    /** 같은 메시지에 reactions 만 교체된 새 record 반환. */
    public MessageResponse withReactions(List<ReactionSummary> rs) {
        return new MessageResponse(
                id, roomId, senderId, senderNickname,
                senderProfileImageUrl, type, content, createdAt,
                attachmentUrl, attachmentMimeType, attachmentSizeBytes,
                editedAt, deletedAt,
                replyToMessageId, replyToSenderNickname, replyToContent, replyToType,
                rs == null ? List.of() : rs
        );
    }
}
