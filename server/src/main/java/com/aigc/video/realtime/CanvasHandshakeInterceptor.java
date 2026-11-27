package com.aigc.video.realtime;

import com.aigc.video.user.UserContext;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/** 握手阶段绑定用户，未登录直接拒绝。 */
@Component
public class CanvasHandshakeInterceptor implements HandshakeInterceptor {

    /** 从安全上下文解析当前用户并写入连接属性，未登录直接拒绝握手。 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            return false;
        }
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
