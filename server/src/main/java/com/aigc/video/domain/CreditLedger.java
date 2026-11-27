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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 流水类型：预占、实扣、退回、充值、会员赠送或调整 */
    private String type;

    /** 关联业务 ID（任务操作 ID、订单号等），与类型共同保证幂等 */
    private String bizId;

    /** 变动积分数 */
    private Integer amount;

    /** 变动后的可用积分快照 */
    private Integer availableAfter;

    /** 变动后的冻结积分快照 */
    private Integer frozenAfter;

    /** 结算状态：冻结、已结算或失败 */
    private String status;

    /** 备注说明 */
    private String note;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
