package com.aigc.video.support;

import com.aigc.video.domain.AppUser;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.user.AuthUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 测试用登录上下文：默认以初始化的管理员身份执行。 */
public final class TestSecurity {

    private TestSecurity() {
    }

    public static AppUser loginAdmin(AppUserMapper userMapper) {
        AppUser admin = userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getRole, AppUser.ROLE_ADMIN)
                .orderByAsc(AppUser::getId)
                .last("LIMIT 1"));
        if (admin == null) {
            throw new IllegalStateException("测试环境未初始化管理员");
        }
        AuthUser principal = new AuthUser(admin.getId(), admin.getUsername(), admin.getPasswordHash(),
                admin.getRole(), admin.isActive(), Boolean.TRUE.equals(admin.getEmailVerified()));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return admin;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
