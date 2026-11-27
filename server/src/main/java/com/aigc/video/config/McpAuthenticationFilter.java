package com.aigc.video.config;

import com.aigc.video.domain.AppUser;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.user.AuthUser;
import com.aigc.video.user.McpTokenService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** MCP 的 SSE 建流和消息端点使用用户级 Bearer Token。 */
@RequiredArgsConstructor
public class McpAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final McpTokenService mcpTokenService;
    private final AppUserMapper appUserMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"/sse".equals(path) && !"/mcp/message".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim() : "";
        AppUser user = mcpTokenService.authenticate(token);
        if (user == null && legacyTokenMatches(token)) {
            user = legacyAdmin();
        }
        if (user == null) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of("message", "MCP Token 无效或缺失"));
            return;
        }

        AuthUser principal = new AuthUser(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole(), user.isActive(), Boolean.TRUE.equals(user.getEmailVerified()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        filterChain.doFilter(request, response);
    }

    /** 兼容旧版全局 MCP_TOKEN，只映射到管理员账号。 */
    private boolean legacyTokenMatches(String token) {
        String configured = properties.getMcpToken();
        if (configured == null || configured.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private AppUser legacyAdmin() {
        String username = properties.getAdminUsername();
        if (username == null || username.isBlank()) {
            return null;
        }
        return appUserMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getUsernameLower, username.trim().toLowerCase())
                .last("LIMIT 1"));
    }
}
