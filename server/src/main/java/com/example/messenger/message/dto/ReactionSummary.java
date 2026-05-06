package com.example.messenger.message.dto;

import java.util.List;

/**
 * 한 메시지의 한 이모지에 대한 집계.
 *  - emoji  : "👍"
 *  - userIds: 그 이모지로 반응한 사용자 id 목록 (정렬: created_at)
 *  - userNicknames: 같은 순서의 닉네임 목록 (호버 툴팁용)
 *
 *  count 는 userIds.size() 로 클라이언트에서 계산.
 *  reactedByMe 도 클라이언트가 me.id ∈ userIds 로 계산 — 서버는 사용자별 캐스팅을 안 한다
 *  (그래야 STOMP 브로드캐스트 페이로드를 모든 멤버에게 같은 모양으로 보낼 수 있다).
 */
public record ReactionSummary(
        String emoji,
        List<Long> userIds,
        List<String> userNicknames
) {}
