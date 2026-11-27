package com.aigc.video.api.mcp;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.workspace.WorkspaceAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** MCP 工具共用的入参校验与序列化辅助 */
@Service
@RequiredArgsConstructor
class McpToolSupport {

    private final WorkspaceAccess workspaceAccess;

    /** 未指定画布时使用当前用户的第一个画布。 */
    long workspace(Long workspaceId) {
        return workspaceAccess.resolveOrDefault(workspaceId);
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    static Long requireId(Long id, String label) {
        if (id == null || id <= 0) {
            throw new BadRequestException("请提供正整数" + label);
        }
        return id;
    }

    /** 返回 JsonNode，避免 MCP 层把字符串再包一层引号（双重转义） */
    static JsonNode toJson(ObjectMapper objectMapper, Object value) {
        return objectMapper.valueToTree(value);
    }
}
