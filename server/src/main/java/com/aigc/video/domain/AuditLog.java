package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户与管理操作审计。 */
@Data
@TableName("audit_log")
public class AuditLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人用户 ID */
    private Long userId;

    /** 操作动作编码，如 LOGIN、ORDER_PAID */
    private String action;

    /** 操作对象类型，如 user、payment_order */
    private String targetType;

    /** 操作对象 ID，统一按字符串保存 */
    private String targetId;

    /** 客户端 IP，优先取 X-Forwarded-For 首个地址 */
    private String ip;

    /** 补充说明，超长时截断 */
    private String detail;

    /** 记录时间 */
    private LocalDateTime createdAt;
}
