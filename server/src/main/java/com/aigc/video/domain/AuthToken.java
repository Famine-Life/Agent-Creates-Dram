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

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String purpose;

    private String tokenHash;

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;
}
