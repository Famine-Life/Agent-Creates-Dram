package com.aigc.video.config;

import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.user.McpTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 多用户 Session + CSRF + 用户级 MCP Token。 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final McpTokenService mcpTokenService;
    private final AppUserMapper appUserMapper;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(properties.isCookieSecure())
                .sameSite("Lax"));

        McpAuthenticationFilter mcpFilter =
                new McpAuthenticationFilter(properties, objectMapper, mcpTokenService, appUserMapper);
        LoginRateLimitFilter loginRateLimitFilter = new LoginRateLimitFilter(properties, objectMapper);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/sse", "/mcp/message", "/ws/**", "/api/payments/notify/**"))
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK, Map.of(
                                        "authenticated", true,
                                        "username", authentication.getName())))
                        .failureHandler((request, response, exception) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of("message", "用户名或密码错误")))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK,
                                        Map.of("authenticated", false))))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Map.of("message", "请先登录")))
                        .accessDeniedHandler((request, response, exception) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        Map.of("message", "请求被拒绝，请刷新页面后重试"))))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                "/api/auth/login", "/api/auth/csrf", "/api/auth/session",
                                "/api/auth/register", "/api/auth/verify-email",
                                "/api/auth/resend-verification", "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/payments/notify/**",
                                "/ws/**")
                        .permitAll()
                        .requestMatchers("/sse", "/mcp/message").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(mcpFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, Map<String, Object> body)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
