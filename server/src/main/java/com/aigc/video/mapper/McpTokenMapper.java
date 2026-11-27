package com.aigc.video.mapper;

import com.aigc.video.domain.McpToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * McpToken 表 Mapper，负责用户级 MCP Token 的哈希存储与鉴权查询。
 */
public interface McpTokenMapper extends BaseMapper<McpToken> {
}
