package com.aigc.video.user;

import com.aigc.video.common.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前请求用户上下文。异步线程没有安全上下文，需显式传递 userId。 */
public final class UserContext {

    private UserContext() {
    }

    /** 读取当前安全上下文中的登录用户，未登录返回 null。 */
    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthUser user ? user : null;
    }

    /** 要求已登录，否则抛出未授权异常。 */
    public static AuthUser require() {
        AuthUser user = current();
        if (user == null) {
            throw new UnauthorizedException("请先登录");
        }
        return user;
    }

    /** 当前用户 ID，未登录返回 null。 */
    public static Long currentUserId() {
        AuthUser user = current();
        return user == null ? null : user.id();
    }

    /** 要求已登录并返回用户 ID。 */
    public static Long requireUserId() {
        return require().id();
    }

    /** 当前用户是否为管理员。 */
    public static boolean isAdmin() {
        AuthUser user = current();
        return user != null && "ADMIN".equals(user.role());
    }
}
