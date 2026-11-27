package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 邮箱验证 / 找回密码 Token，只保存哈希。 */
@Data
@TableName("auth_token")
public class AuthToken {

    public static final String PURPOSE_VERIFY = "VERIFY_EMAIL";
    public static final String PURPOSE_RESET = "RESET_PASSWORD";

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 用途：邮箱验证或重置密码 */
    private String purpose;

    /** Token 的 SHA-256 哈希，明文只在邮件链接中出现 */
    private String tokenHash;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 使用时间，为空表示尚未使用 */
    private LocalDateTime usedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
