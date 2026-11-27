package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 不可变积分流水。 */
@Data
@TableName("credit_ledger")
public class CreditLedger {

    public static final String TYPE_RESERVE = "RESERVE";
    public static final String TYPE_CAPTURE = "CAPTURE";
    public static final String TYPE_RELEASE = "RELEASE";
    public static final String TYPE_RECHARGE = "RECHARGE";
    public static final String TYPE_MEMBER_GRANT = "MEMBER_GRANT";
    public static final String TYPE_ADJUST = "ADJUST";
    public static final String TYPE_INSUFFICIENT = "INSUFFICIENT";

    public static final String STATUS_FROZEN = "FROZEN";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String type;

    private String bizId;

    private Integer amount;

    private Integer availableAfter;

    private Integer frozenAfter;

    private String status;

    private String note;

    private LocalDateTime createdAt;
}
