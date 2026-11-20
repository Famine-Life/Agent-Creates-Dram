package com.aigc.video.config;

import com.aigc.video.api.mcp.McpImageTools;
import com.aigc.video.api.mcp.McpNodeTools;
import com.aigc.video.api.mcp.McpShotTools;
import com.aigc.video.api.mcp.McpWorkspaceTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 把 @Tool 方法注册成 MCP 工具，端点 /sse（SSE 传输） */
/**
 * 配置应用组件与运行参数。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider canvasToolCallbacks(McpWorkspaceTools workspaceTools,
                                                    McpShotTools shotTools,
                                                    McpImageTools imageTools,
                                                    McpNodeTools nodeTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(workspaceTools, shotTools, imageTools, nodeTools)
                .build();
    }
}
