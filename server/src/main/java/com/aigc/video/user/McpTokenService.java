package com.aigc.video.user;

import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.McpToken;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.McpTokenMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 用户级 MCP Token：只保存哈希，明文仅在创建时返回一次。 */
@Service
@RequiredArgsConstructor
public class McpTokenService {

    private final McpTokenMapper tokenMapper;
    private final AppUserMapper userMapper;

    @Transactional
    public CreatedToken create(Long userId, String name, Integer expiresInDays) {
        String raw = "aigc_" + AuthService.randomToken();
        McpToken token = new McpToken();
        token.setUserId(userId);
        token.setName(name == null || name.isBlank() ? "默认 Token" : name.trim());
        token.setTokenHash(AuthService.sha256(raw));
        token.setTokenPrefix(raw.substring(0, Math.min(12, raw.length())));
        if (expiresInDays != null && expiresInDays > 0) {
            token.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        }
        token.setCreatedAt(LocalDateTime.now());
        tokenMapper.insert(token);
        return new CreatedToken(token.getId(), token.getName(), token.getTokenPrefix(),
                token.getExpiresAt(), raw);
    }

    public List<McpToken> list(Long userId) {
        return tokenMapper.selectList(Wrappers.<McpToken>lambdaQuery()
                .eq(McpToken::getUserId, userId)
                .orderByDesc(McpToken::getId));
    }

    @Transactional
    public void revoke(Long userId, Long id) {
        McpToken token = tokenMapper.selectOne(Wrappers.<McpToken>lambdaQuery()
                .eq(McpToken::getId, id).eq(McpToken::getUserId, userId).last("LIMIT 1"));
        if (token == null) {
            throw new NotFoundException("Token 不存在");
        }
        tokenMapper.update(null, Wrappers.<McpToken>lambdaUpdate()
                .eq(McpToken::getId, id)
                .set(McpToken::getRevokedAt, LocalDateTime.now()));
    }

    /** 校验 Bearer Token 并解析出用户。 */
    @Transactional
    public AppUser authenticate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        McpToken token = tokenMapper.selectOne(Wrappers.<McpToken>lambdaQuery()
                .eq(McpToken::getTokenHash, AuthService.sha256(raw.trim()))
                .last("LIMIT 1"));
        if (token == null || token.getRevokedAt() != null) {
            return null;
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        AppUser user = userMapper.selectById(token.getUserId());
        if (user == null || !user.isActive()) {
            return null;
        }
        tokenMapper.update(null, Wrappers.<McpToken>lambdaUpdate()
                .eq(McpToken::getId, token.getId())
                .set(McpToken::getLastUsedAt, LocalDateTime.now()));
        return user;
    }

    public record CreatedToken(Long id, String name, String prefix, LocalDateTime expiresAt, String token) {
    }
}
