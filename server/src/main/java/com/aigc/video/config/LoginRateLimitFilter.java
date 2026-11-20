package com.aigc.video.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录接口的暴力破解防护：按客户端 IP 做短时限速，并随连续失败次数渐进封禁。
 *
 * <p>部署在 Nginx 之后时，依赖 {@code server.forward-headers-strategy=framework} 把
 * X-Forwarded-For 解析到 {@link HttpServletRequest#getRemoteAddr()}；应用端口不应直接暴露公网，
 * 否则客户端可以伪造 X-Forwarded-For 绕过统计。
 */
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    /** 短时限速：同一 IP 每分钟最多 10 次登录请求，超出返回 429 但不封禁。 */
    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    /** 缓存上限，避免大量轮换 IP 把内存撑爆。 */
    private static final int MAX_TRACKED_IPS = 4096;
    private static final Duration IDLE_EXPIRY = Duration.ofHours(25);

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, IpState> states = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_PATH.equals(request.getServletPath())
                || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank() || isTrusted(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        purgeIfNeeded(now);
        IpState state = states.computeIfAbsent(ip, key -> new IpState(now));

        long retryAfterSeconds = 0;
        String rejection = null;
        synchronized (state) {
            state.lastSeen = now;
            if (state.banUntil > now) {
                retryAfterSeconds = (state.banUntil - now + 999) / 1000;
                rejection = "登录尝试过于频繁，请 " + minutesOf(retryAfterSeconds) + " 分钟后再试";
            } else {
                if (now - state.windowStart >= WINDOW_SECONDS * 1000L) {
                    state.windowStart = now;
                    state.windowCount = 0;
                }
                if (++state.windowCount > MAX_REQUESTS_PER_WINDOW) {
                    retryAfterSeconds = Math.max(1,
                            (state.windowStart + WINDOW_SECONDS * 1000L - now + 999) / 1000);
                    rejection = "登录请求过于频繁，请稍后再试";
                }
            }
        }
        if (rejection != null) {
            reject(response, rejection, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
        recordResult(ip, state, response.getStatus());
    }

    /** 登录成功清零；401 累加失败次数并按梯度封禁。 */
    private void recordResult(String ip, IpState state, int status) {
        long now = System.currentTimeMillis();
        synchronized (state) {
            state.lastSeen = now;
            if (status >= 200 && status < 400) {
                state.failures = 0;
                state.banUntil = 0;
                return;
            }
            if (status != HttpServletResponse.SC_UNAUTHORIZED) {
                return;
            }
            state.failures++;
            long banMillis = banMillisFor(state.failures);
            if (banMillis > 0) {
                state.banUntil = now + banMillis;
                log.warn("登录连续失败 {} 次，封禁 IP {} {} 分钟", state.failures, ip, banMillis / 60_000);
            }
        }
    }

    /** 5 次失败封 15 分钟，10 次封 1 小时，20 次起封 24 小时。 */
    private static long banMillisFor(int failures) {
        if (failures >= 20) {
            return Duration.ofHours(24).toMillis();
        }
        if (failures >= 10) {
            return Duration.ofHours(1).toMillis();
        }
        if (failures >= 5) {
            return Duration.ofMinutes(15).toMillis();
        }
        return 0;
    }

    private boolean isTrusted(String ip) {
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        List<String> allowlist = properties.getLoginIpAllowlist();
        return allowlist != null && allowlist.stream()
                .filter(entry -> entry != null)
                .map(String::trim)
                .anyMatch(ip::equals);
    }

    private void purgeIfNeeded(long now) {
        if (states.size() <= MAX_TRACKED_IPS) {
            return;
        }
        states.entrySet().removeIf(entry -> {
            IpState state = entry.getValue();
            synchronized (state) {
                return state.banUntil <= now && now - state.lastSeen > IDLE_EXPIRY.toMillis();
            }
        });
        if (states.size() <= MAX_TRACKED_IPS) {
            return;
        }
        // 仍然超限时按最后活跃时间淘汰最旧的一批，优先保证正常请求可继续被统计。
        List<String> oldest = states.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastSeen))
                .limit(Math.max(1, states.size() / 10))
                .map(Map.Entry::getKey)
                .toList();
        oldest.forEach(states::remove);
    }

    private void reject(HttpServletResponse response, String message, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }

    private static long minutesOf(long seconds) {
        return Math.max(1, (seconds + 59) / 60);
    }

    private static final class IpState {
        private long lastSeen;
        private long windowStart;
        private int windowCount;
        private int failures;
        private long banUntil;

        private IpState(long now) {
            this.lastSeen = now;
            this.windowStart = now;
        }
    }
}
