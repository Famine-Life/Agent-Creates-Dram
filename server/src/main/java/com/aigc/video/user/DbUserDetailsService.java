package com.aigc.video.user;

import com.aigc.video.domain.AppUser;
import com.aigc.video.mapper.AppUserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 从数据库加载登录用户。 */
@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserMapper userMapper;

    /** 按用户名或邮箱（忽略大小写）加载登录用户。 */
    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        String key = account == null ? "" : account.trim().toLowerCase();
        AppUser user = userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .and(w -> w.eq(AppUser::getUsernameLower, key).or().eq(AppUser::getEmailLower, key))
                .last("LIMIT 1"));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new AuthUser(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(),
                user.isActive(), Boolean.TRUE.equals(user.getEmailVerified()));
    }
}
