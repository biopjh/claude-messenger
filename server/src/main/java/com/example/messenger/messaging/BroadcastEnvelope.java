package com.example.messenger.messaging;

/**
 * Redis Pub/Sub 채널을 통해 인스턴스 간에 주고받는 봉투.
 *
 *  - instanceId : 발신 인스턴스 식별자 (자기 자신이 발행한 메시지를 다시 받지 않게 거름)
 *  - destination: STOMP 목적지 (e.g. "/topic/rooms/42" or "/queue/notifications")
 *  - payload    : 그대로 STOMP 클라이언트로 전달될 객체. JSON 직렬화 후 LinkedHashMap 으로 역직렬화됨
 *  - user       : null 이면 broadcast(convertAndSend), non-null 이면 user 별 큐(convertAndSendToUser)
 */
public record BroadcastEnvelope(
        String instanceId,
        String destination,
        Object payload,
        String user
) {}
