package com.aigc.video.api.dto;

/** 认证与账户请求体。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 注册请求：用户名、邮箱与明文密码。 */
    public record RegisterRequest(String username, String email, String password) {
    }

    /** 邮箱验证请求：一次性 Token。 */
    public record TokenRequest(String token) {
    }

    /** 邮箱地址请求：用于重发验证邮件或找回密码。 */
    public record EmailRequest(String email) {
    }

    /** 重置密码请求：一次性 Token 与新密码。 */
    public record ResetRequest(String token, String password) {
    }

    /** 创建 MCP Token 请求：名称与有效天数，均可省略。 */
    public record McpTokenRequest(String name, Integer expiresInDays) {
    }

    /** 修改资料请求：新邮箱。 */
    public record ProfileRequest(String email) {
    }
}
