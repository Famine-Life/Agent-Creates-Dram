package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户级 MCP Token。 */
@Data
@TableName("mcp_token")
public class McpToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String tokenHash;

    private String tokenPrefix;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;
}
