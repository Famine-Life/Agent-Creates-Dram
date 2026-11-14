package com.aigc.video.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Redis Pub/Sub 版画布事件中继：AIGC_REDIS_ENABLED=true 时启用 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aigc.realtime.redis-enabled", havingValue = "true")
public class RedisCanvasEventRelay implements CanvasEventRelay {

    public static final String CHANNEL = "aigc:canvas:events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean publish(Long workspaceId, CanvasEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "workspaceId", workspaceId,
                    "event", event));
            redisTemplate.convertAndSend(CHANNEL, payload);
            return true;
        } catch (Exception e) {
            log.warn("Redis 广播失败，回退本地推送：{}", e.getMessage());
            return false;
        }
    }
}
