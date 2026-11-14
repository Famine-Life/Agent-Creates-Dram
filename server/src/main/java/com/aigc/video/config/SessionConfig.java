package com.aigc.video.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 多实例共享登录态：AIGC_SESSION_REDIS=true 时把 Session 存到 Redis，任一实例签发的会话其它实例都能识别。
 * 默认关闭，沿用容器内存 Session，保证单机无 Redis 也能登录。
 */
@Configuration
@ConditionalOnProperty(name = "aigc.session.redis-enabled", havingValue = "true")
@EnableRedisHttpSession(redisNamespace = "aigc:session")
public class SessionConfig {
}
