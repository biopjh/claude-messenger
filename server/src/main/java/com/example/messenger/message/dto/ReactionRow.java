package com.example.messenger.message.dto;

/**
 * message_reactions 의 한 행을 사용자 닉네임 join 한 평면 형태.
 * 서비스 레이어에서 messageId/emoji 별로 그룹핑해서 ReactionSummary 로 변환한다.
 */
public record ReactionRow(
        Long messageId,
        String emoji,
        Long userId,
        String userNickname
) {}
