package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台用户。 */
@Data
@TableName("app_user")
public class AppUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，保留用户输入的原始大小写 */
    private String username;

    /** 用户名小写副本，用于唯一性与登录查询 */
    private String usernameLower;

    /** 邮箱，保留用户输入的原始大小写 */
    private String email;

    /** 邮箱小写副本，用于唯一性与登录查询 */
    private String emailLower;

    /** 密码哈希（BCrypt），不保存明文 */
    private String passwordHash;

    /** 角色：USER 或 ADMIN */
    private String role;

    /** 账号状态：ACTIVE 或 DISABLED */
    private String status;

    /** 邮箱是否已完成验证，未验证不能生成和充值 */
    private Boolean emailVerified;

    /** 当前会员档位编码，对应 member_plan.code */
    private String memberCode;

    /** 会员到期时间，过期后回落到免费档 */
    private LocalDateTime memberExpiresAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
