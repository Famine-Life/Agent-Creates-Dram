package com.aigc.video.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 画布连接注册表。
 * 单实例直接推送本地 session；开启 Redis 后先 publish，由各实例订阅后再推送本地连接。
 */
/**
 * 处理画布实时事件的推送与转发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasSocketRegistry {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<CanvasEventRelay> relayProvider;

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void join(Long workspaceId, WebSocketSession session) {
        sessions.computeIfAbsent(workspaceId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("画布连接建立 workspaceId={} sessionId={} 当前连接数={}",
                workspaceId, session.getId(), sessions.get(workspaceId).size());
    }

    public void leave(Long workspaceId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(workspaceId);
        if (set != null) {
            set.remove(session);
        }
    }

    /**
     * 对外广播入口：有 Redis 中继时走 Pub/Sub（各实例收到后本地推送），否则直接推本实例。
     */
    public void broadcast(Long workspaceId, CanvasEvent event) {
        CanvasEventRelay relay = relayProvider.getIfAvailable();
        if (relay != null && relay.publish(workspaceId, event)) {
            return;
        }
        deliverLocal(workspaceId, event);
    }

    /** 只推送给当前实例持有的连接 */
    public void deliverLocal(Long workspaceId, CanvasEvent event) {
        Set<WebSocketSession> set = sessions.get(workspaceId);
        if (set == null || set.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("序列化画布事件失败", e);
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : set) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            } catch (IOException e) {
                log.debug("推送失败，忽略 sessionId={}", session.getId());
            }
        }
    }

    public void send(WebSocketSession session, CanvasEvent event) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            log.debug("发送消息失败 sessionId={}", session.getId());
        }
    }
}
