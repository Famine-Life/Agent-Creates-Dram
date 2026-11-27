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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 ID */
    private Long userId;

    /** Token 名称，便于用户区分 */
    private String name;

    /** Token 的 SHA-256 哈希，不保存明文 */
    private String tokenHash;

    /** Token 前缀，列表展示时用于识别 */
    private String tokenPrefix;

    /** 过期时间，为空表示长期有效 */
    private LocalDateTime expiresAt;

    /** 吊销时间，非空表示已失效 */
    private LocalDateTime revokedAt;

    /** 最近使用时间 */
    private LocalDateTime lastUsedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
