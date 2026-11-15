package com.aigc.video.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 连接地址：ws://host:8080/ws/canvas/{workspaceId}
 */
/**
 * 处理画布实时事件的推送与转发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasWebSocketHandler extends TextWebSocketHandler {

    private final CanvasSocketRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long workspaceId = resolveWorkspaceId(session);
        if (workspaceId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("workspaceId 缺失"));
            return;
        }
        session.getAttributes().put("workspaceId", workspaceId);
        registry.join(workspaceId, session);
        registry.send(session, CanvasEvent.of("connected", java.util.Map.of("workspaceId", workspaceId)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            registry.send(session, CanvasEvent.of("pong", null));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object workspaceId = session.getAttributes().get("workspaceId");
        if (workspaceId instanceof Long id) {
            registry.leave(id, session);
        }
    }

    private Long resolveWorkspaceId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        try {
            return Long.parseLong(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
