package com.aigc.video.user;

import com.aigc.video.common.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前请求用户上下文。异步线程没有安全上下文，需显式传递 userId。 */
public final class UserContext {

    private UserContext() {
    }

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthUser user ? user : null;
    }

    public static AuthUser require() {
        AuthUser user = current();
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        return user;
    }

    public static Long currentUserId() {
        AuthUser user = current();
        return user == null ? null : user.id();
    }

    public static Long requireUserId() {
        return require().id();
    }

    public static boolean isAdmin() {
        AuthUser user = current();
        return user != null && "ADMIN".equals(user.role());
    }
}
