package com.aigc.video.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 订阅 Redis 画布事件，并推送给本实例的 WebSocket 连接 */
/**
 * 处理画布实时事件的推送与转发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aigc.realtime.redis-enabled", havingValue = "true")
public class RedisCanvasSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final CanvasSocketRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            CanvasEvent event = objectMapper.treeToValue(root.path("event"), CanvasEvent.class);
            registry.deliverLocal(root.path("workspaceId").asLong(), event);
        } catch (Exception e) {
            log.warn("处理 Redis 画布事件失败：{}", e.getMessage());
        }
    }
}
