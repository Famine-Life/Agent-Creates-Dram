package com.aigc.video.mapper;

import com.aigc.video.domain.AuthToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * AuthToken 表 Mapper，负责邮箱验证与重置密码 Token 的读写。
 */
public interface AuthTokenMapper extends BaseMapper<AuthToken> {
}
