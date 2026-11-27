package com.aigc.video.api.dto;

/** 认证与账户请求体。 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    public record TokenRequest(String token) {
    }

    public record EmailRequest(String email) {
    }

    public record ResetRequest(String token, String password) {
    }

    public record McpTokenRequest(String name, Integer expiresInDays) {
    }

    public record ProfileRequest(String email) {
    }
}
