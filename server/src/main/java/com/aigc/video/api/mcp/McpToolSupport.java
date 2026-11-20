package com.aigc.video.api.mcp;

import com.aigc.video.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** MCP 工具共用的入参校验与序列化辅助 */
final class McpToolSupport {

    private McpToolSupport() {
    }

    /** 未指定画布时使用默认画布 1，与前端和 CLI 保持一致 */
    static long workspace(Long workspaceId) {
        return workspaceId == null || workspaceId <= 0 ? 1L : workspaceId;
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
