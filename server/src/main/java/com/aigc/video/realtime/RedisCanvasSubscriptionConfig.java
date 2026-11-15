package com.aigc.video.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Redis 订阅容器：监听画布事件频道 */
/**
 * 处理画布实时事件的推送与转发。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aigc.realtime.redis-enabled", havingValue = "true")
public class RedisCanvasSubscriptionConfig {

    private final RedisCanvasSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer canvasRedisListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisCanvasEventRelay.CHANNEL));
        return container;
    }
}
