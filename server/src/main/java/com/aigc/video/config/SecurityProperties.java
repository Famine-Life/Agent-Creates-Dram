package com.aigc.video.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置应用组件与运行参数。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "aigc.security")
public class SecurityProperties {

    @NotBlank(message = "必须通过 AIGC_ADMIN_USERNAME 配置管理员用户名")
    private String adminUsername;

    @NotBlank(message = "必须通过 AIGC_ADMIN_PASSWORD 配置管理员密码")
    private String adminPassword;

    @NotBlank(message = "必须通过 MCP_API_TOKEN 配置 MCP Token")
    @Size(min = 8, message = "MCP_API_TOKEN 至少需要 8 个字符")
    private String mcpToken;

    /** HTTPS 公网部署必须设为 true */
    private boolean cookieSecure;

    /** 额外允许的跨域来源；同源始终允许 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 登录限流白名单 IP：不参与失败计数与封禁，用于固定出口 IP 自救；回环地址始终放行。 */
    private List<String> loginIpAllowlist = new ArrayList<>();

    public String[] allowedOriginPatterns() {
        return allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }
}
