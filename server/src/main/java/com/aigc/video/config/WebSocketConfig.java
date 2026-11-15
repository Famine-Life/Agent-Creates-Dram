package com.aigc.video.config;

import com.aigc.video.realtime.CanvasWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 配置应用组件与运行参数。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CanvasWebSocketHandler canvasWebSocketHandler;
    private final SecurityProperties securityProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(canvasWebSocketHandler, "/ws/canvas/*");
        String[] origins = securityProperties.allowedOriginPatterns();
        // 不配置额外来源时，Spring 默认只允许同源；握手请求同时由 Spring Security 校验 Session。
        if (origins.length > 0) {
            registration.setAllowedOriginPatterns(origins);
        }
    }
}
