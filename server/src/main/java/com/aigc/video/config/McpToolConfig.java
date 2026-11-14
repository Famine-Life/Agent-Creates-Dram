package com.aigc.video.config;

import com.aigc.video.api.mcp.McpCanvasTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把 @Tool 方法注册成 MCP 工具，端点 /sse（SSE 传输） */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider canvasToolCallbacks(McpCanvasTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
