package com.aigc.video.user;

import com.aigc.video.api.dto.AuthDtos;
import com.aigc.video.audit.AuditService;
import com.aigc.video.billing.WalletService;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.AuthToken;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.AuthTokenMapper;
import com.aigc.video.member.MembershipService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 注册、邮箱验证、找回密码与个人 MCP Token 的生命周期。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserMapper userMapper;
    private final AuthTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final WalletService walletService;
    private final MembershipService membershipService;
    private final AuditService auditService;

    @Value("${aigc.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public Map<String, Object> register(AuthDtos.RegisterRequest request) {
        String username = request == null || request.username() == null ? "" : request.username().trim();
        String email = request == null || request.email() == null ? "" : request.email().trim();
        String password = request == null || request.password() == null ? "" : request.password();
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("用户名需为 3-32 位字母、数字或下划线");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 190) {
            throw new BadRequestException("邮箱格式不正确");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new BadRequestException("密码长度需为 8-72 位");
        }
        String usernameLower = username.toLowerCase();
        String emailLower = email.toLowerCase();
        if (userMapper.selectCount(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getUsernameLower, usernameLower)) > 0) {
            throw new BadRequestException("用户名已被使用");
        }
        if (userMapper.selectCount(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmailLower, emailLower)) > 0) {
            throw new BadRequestException("邮箱已被注册");
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setUsernameLower(usernameLower);
        user.setEmail(email);
        user.setEmailLower(emailLower);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(AppUser.ROLE_USER);
        user.setStatus(AppUser.STATUS_ACTIVE);
        user.setEmailVerified(false);
        user.setMemberCode(MemberPlan.FREE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        walletService.ensureWallet(user.getId());
        // 新用户赠送免费档的每日额度，便于完成“注册→验证→生成”的首个闭环
        int welcome = membershipService.resolve(user).getDailyCredits() == null
                ? 0 : membershipService.resolve(user).getDailyCredits();
        if (welcome > 0) {
            walletService.credit(user.getId(), com.aigc.video.domain.CreditLedger.TYPE_MEMBER_GRANT,
                    "welcome-" + user.getId(), welcome, "新用户赠送积分");
        }
        String link = issueToken(user, AuthToken.PURPOSE_VERIFY, 24 * 60, "/verify-email");
        auditService.record(user.getId(), "USER_REGISTER", "user", user.getId(), "注册账号 " + username);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("username", username);
        if (link != null) {
            result.put("devVerifyLink", link);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> verifyEmail(String rawToken) {
        AuthToken token = requireToken(rawToken, AuthToken.PURPOSE_VERIFY);
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .eq(AppUser::getId, token.getUserId())
                .set(AppUser::getEmailVerified, true)
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
        markUsed(token);
        auditService.record(token.getUserId(), "EMAIL_VERIFIED", "user", token.getUserId(), null);
        return Map.of("ok", true);
    }

    public Map<String, Object> resendVerification(String email) {
        AppUser user = findVerifiedOrUnverified(email);
        // 无论账号是否存在都返回相同结果，避免账号枚举
        if (user == null || Boolean.TRUE.equals(user.getEmailVerified())) {
            return Map.of("ok", true);
        }
        String link = issueToken(user, AuthToken.PURPOSE_VERIFY, 24 * 60, "/verify-email");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        if (link != null) {
            result.put("devVerifyLink", link);
        }
        return result;
    }

    public Map<String, Object> forgotPassword(String email) {
        AppUser user = findVerifiedOrUnverified(email);
        if (user == null || !user.isActive()) {
            return Map.of("ok", true);
        }
        String link = issueToken(user, AuthToken.PURPOSE_RESET, 30, "/reset-password");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        if (link != null) {
            result.put("devResetLink", link);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> resetPassword(String rawToken, String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new BadRequestException("密码长度需为 8-72 位");
        }
        AuthToken token = requireToken(rawToken, AuthToken.PURPOSE_RESET);
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .eq(AppUser::getId, token.getUserId())
                .set(AppUser::getPasswordHash, passwordEncoder.encode(password))
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
        markUsed(token);
        auditService.record(token.getUserId(), "PASSWORD_RESET", "user", token.getUserId(), null);
        return Map.of("ok", true);
    }

    public AppUser requireUser(Long userId) {
        AppUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private AppUser findVerifiedOrUnverified(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getEmailLower, email.trim().toLowerCase()).last("LIMIT 1"));
    }

    /** 生成一次性 Token 并发送邮件；log 模式返回开发链接。 */
    private String issueToken(AppUser user, String purpose, int ttlMinutes, String path) {
        String raw = randomToken();
        tokenMapper.delete(Wrappers.<AuthToken>lambdaQuery()
                .eq(AuthToken::getUserId, user.getId())
                .eq(AuthToken::getPurpose, purpose)
                .isNull(AuthToken::getUsedAt));
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setPurpose(purpose);
        token.setTokenHash(sha256(raw));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        token.setCreatedAt(LocalDateTime.now());
        tokenMapper.insert(token);

        String link = publicBaseUrl + path + "?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
        String subject = AuthToken.PURPOSE_RESET.equals(purpose) ? "重置密码" : "验证邮箱";
        mailService.send(user.getEmail(), "Agent造剧｜" + subject,
                "你好 " + user.getUsername() + "：\n\n请打开以下链接" + subject + "：\n" + link
                        + "\n\n链接有效期 " + ttlMinutes + " 分钟，如非本人操作请忽略。");
        return mailService.logMode() ? link : null;
    }

    private AuthToken requireToken(String raw, String purpose) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("链接无效或已过期");
        }
        AuthToken token = tokenMapper.selectOne(Wrappers.<AuthToken>lambdaQuery()
                .eq(AuthToken::getTokenHash, sha256(raw.trim()))
                .eq(AuthToken::getPurpose, purpose)
                .last("LIMIT 1"));
        if (token == null || token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("链接无效或已过期");
        }
        return token;
    }

    private void markUsed(AuthToken token) {
        tokenMapper.update(null, Wrappers.<AuthToken>lambdaUpdate()
                .eq(AuthToken::getId, token.getId())
                .set(AuthToken::getUsedAt, LocalDateTime.now()));
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算哈希", e);
        }
    }

    public MembershipService membership() {
        return membershipService;
    }
}
