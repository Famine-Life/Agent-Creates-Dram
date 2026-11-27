package com.aigc.video.api;

import com.aigc.video.api.dto.AuthDtos;
import com.aigc.video.billing.AccountService;
import com.aigc.video.user.AuthService;
import com.aigc.video.user.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 认证、注册、邮箱验证与密码找回。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

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
                && !(authentication instanceof AnonymousAuthenticationToken)
                && UserContext.current() != null;
        if (!authenticated) {
            return Map.of("authenticated", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authenticated", true);
        result.put("username", authentication.getName());
        result.put("user", accountService.summary(UserContext.requireUserId()));
        return result;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-email")
    public Map<String, Object> verifyEmail(@RequestBody AuthDtos.TokenRequest request) {
        return authService.verifyEmail(request == null ? null : request.token());
    }

    @PostMapping("/resend-verification")
    public Map<String, Object> resend(@RequestBody AuthDtos.EmailRequest request) {
        return authService.resendVerification(request == null ? null : request.email());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgot(@RequestBody AuthDtos.EmailRequest request) {
        return authService.forgotPassword(request == null ? null : request.email());
    }

    @PostMapping("/reset-password")
    public Map<String, Object> reset(@RequestBody AuthDtos.ResetRequest request) {
        return authService.resetPassword(request == null ? null : request.token(),
                request == null ? null : request.password());
    }
}
