package com.aigc.video.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 多实例调度锁；未开启 Redis 时按单实例运行，锁异常时暂停调度。 */
@Slf4j
@Component
public class DispatchLock {

    private static final String KEY = "aigc:dispatch:lock";
    private static final Duration TTL = Duration.ofSeconds(120);
    private static final String LOCAL_TOKEN = "local";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    public DispatchLock(StringRedisTemplate redisTemplate,
                        @Value("${aigc.realtime.redis-enabled:false}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    /** 获取本轮调度权；返回 null 表示其它实例正在调度，本次直接跳过 */
    public String tryAcquire() {
        if (!enabled) {
            return LOCAL_TOKEN;
        }
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY, token, TTL);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (Exception e) {
            log.error("调度锁获取失败，本轮暂停任务调度：{}", e.getMessage());
            return null;
        }
    }

    public void release(String token) {
        if (!enabled || token == null || LOCAL_TOKEN.equals(token)) {
            return;
        }
        try {
            redisTemplate.execute(RELEASE, List.of(KEY), token);
        } catch (Exception e) {
            log.warn("调度锁释放失败，将在 {} 秒后自动过期：{}", TTL.toSeconds(), e.getMessage());
        }
    }
}
