package com.aigc.video.api;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供应用的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 访问 token 会触发 CsrfTokenRepository 写入 XSRF-TOKEN Cookie。 */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName());
    }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            return Map.of("authenticated", false);
        }
        return Map.of("authenticated", true, "username", authentication.getName());
    }
}
