package com.example.messenger.messaging;

import com.example.messenger.config.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 모든 STOMP 브로드캐스트는 이 클래스를 거쳐 나간다.
 *
 *   send(dest, payload)        → 로컬 인스턴스 + (Redis 가 켜져 있으면) 다른 인스턴스에도 전파
 *   sendToUser(user, dest, payload) → 같은 동작, user 별 큐
 *
 * Redis 가 비활성화(messenger.redis.enabled=false 또는 RedisTemplate 빈이 없는 경우)면
 * 로컬 SimpMessagingTemplate 만 호출 — 단일 인스턴스에서는 평상시와 동일하게 동작한다.
 */
@Slf4j
@Component
public class MessageBroker {

    private final SimpMessagingTemplate local;
    private final ObjectMapper objectMapper;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /** Redis 가 활성화되었을 때만 주입. 없으면 null → publish 스킵. */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    public MessageBroker(SimpMessagingTemplate local, ObjectMapper objectMapper) {
        this.local = local;
        this.objectMapper = objectMapper;
        log.info("[MessageBroker] instanceId={}", instanceId);
    }

    public String getInstanceId() { return instanceId; }

    /** /topic/... 같은 broadcast 목적지로 전송. */
    public void send(String destination, Object payload) {
        local.convertAndSend(destination, payload);
        publishToRedis(destination, payload, null);
    }

    /** 특정 사용자의 /user/queue/... 로 전송. */
    public void sendToUser(String user, String destination, Object payload) {
        local.convertAndSendToUser(user, destination, payload);
        publishToRedis(destination, payload, user);
    }

    private void publishToRedis(String destination, Object payload, String user) {
        if (redis == null) return;
        try {
            BroadcastEnvelope env = new BroadcastEnvelope(instanceId, destination, payload, user);
            String json = objectMapper.writeValueAsString(env);
            redis.convertAndSend(RedisConfig.CHANNEL, json);
        } catch (Exception e) {
            log.warn("[MessageBroker] Redis publish failed: {}", e.getMessage());
        }
    }

    /**
     * Redis 채널에서 다른 인스턴스가 발행한 메시지를 수신하면 호출됨.
     * 자기 자신이 보낸 거면 무시(echo 방지). 그렇지 않으면 로컬 broker 로 forward.
     *
     * RedisMessageListenerContainer 의 MessageListenerAdapter 가 이 메서드 이름을 호출하도록
     * RedisConfig 에서 설정한다.
     */
    public void onRedisMessage(String json) {
        try {
            BroadcastEnvelope env = objectMapper.readValue(json, BroadcastEnvelope.class);
            if (instanceId.equals(env.instanceId())) return;     // 자신이 보낸 메시지면 스킵
            if (env.user() != null) {
                local.convertAndSendToUser(env.user(), env.destination(), env.payload());
            } else {
                local.convertAndSend(env.destination(), env.payload());
            }
        } catch (Exception e) {
            log.warn("[MessageBroker] Redis subscribe parse failed: {}", e.getMessage());
        }
    }
}
