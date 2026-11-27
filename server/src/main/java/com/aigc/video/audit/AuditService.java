package com.aigc.video.audit;

import com.aigc.video.domain.AuditLog;
import com.aigc.video.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/** 审计日志：只记录动作与目标，不记录密码、Token 或支付密钥。 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditMapper;

    /** 记录一条审计日志，detail 超长时截断到 500 字符。 */
    public void record(Long userId, String action, String targetType, Object targetId, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setIp(clientIp());
        log.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
