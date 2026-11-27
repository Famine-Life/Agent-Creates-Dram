package com.aigc.video.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** 登录主体，携带用户 ID 供租户校验使用。 */
public record AuthUser(
        /** 用户 ID，用于租户与归属校验 */
        Long id,
        /** 用户名 */
        String username,
        /** 密码哈希 */
        String password,
        /** 角色，映射为 ROLE_ 权限 */
        String role,
        /** 账号是否可用 */
        boolean enabled,
        /** 邮箱是否已验证 */
        boolean emailVerified
) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
