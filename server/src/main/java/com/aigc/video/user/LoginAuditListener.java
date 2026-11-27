package com.aigc.video.user;

import com.aigc.video.audit.AuditService;
import com.aigc.video.domain.AppUser;
import com.aigc.video.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 登录成功后刷新最后登录时间并记录审计。 */
@Component
@RequiredArgsConstructor
public class LoginAuditListener {

    private final AppUserMapper userMapper;
    private final AuditService auditService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication().getPrincipal() instanceof AuthUser principal)) {
            return;
        }
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .eq(AppUser::getId, principal.id())
                .set(AppUser::getLastLoginAt, LocalDateTime.now()));
        auditService.record(principal.id(), "LOGIN", "user", principal.id(), null);
    }
}
