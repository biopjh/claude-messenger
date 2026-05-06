package com.example.messenger.config;

import com.example.messenger.messaging.MessageBroker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Pub/Sub 으로 인스턴스 간 STOMP 브로드캐스트를 전파한다.
 * messenger.redis.enabled=true 일 때만 활성화 — dev 기본값은 false 라 Redis 안 띄워도 괜찮음.
 */
@Configuration
@ConditionalOnProperty(name = "messenger.redis.enabled", havingValue = "true")
public class RedisConfig {

    /** 모든 인스턴스가 구독/발행하는 단일 채널. */
    public static final String CHANNEL = "messenger:broadcast";

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    /**
     * Redis 채널에서 들어오는 메시지를 MessageBroker.onRedisMessage(String) 로 위임.
     * 메시지 본문은 StringRedisSerializer 로 String 으로 변환되어 메서드에 전달된다.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageBroker broker
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListenerAdapter adapter = new MessageListenerAdapter(broker, "onRedisMessage");
        adapter.setSerializer(new StringRedisSerializer());
        adapter.afterPropertiesSet();

        container.addMessageListener(adapter, new ChannelTopic(CHANNEL));
        return container;
    }
}
