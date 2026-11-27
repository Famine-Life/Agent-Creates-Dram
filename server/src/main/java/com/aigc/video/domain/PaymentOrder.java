package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 积分 / 会员订单。 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    public static final String TYPE_CREDITS = "CREDITS";
    public static final String TYPE_MEMBERSHIP = "MEMBERSHIP";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAYING = "PAYING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CLOSED = "CLOSED";

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台订单号，对外唯一 */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 商品类型：积分或会员 */
    private String productType;

    /** 商品编码（积分套餐或会员档位） */
    private String productCode;

    /** 下单时的商品快照 JSON，避免商品改动影响历史订单 */
    private String productSnapshot;

    /** 应付金额（分） */
    private Integer amountCents;

    /** 积分商品对应的积分数量 */
    private Integer credits;

    /** 会员商品对应的档位编码 */
    private String planCode;

    /** 订单状态：待支付、支付中、已支付或已关闭 */
    private String status;

    /** 支付渠道 */
    private String channel;

    /** 第三方交易号 */
    private String thirdPartyTradeNo;

    /** 支付超时时间 */
    private LocalDateTime expiresAt;

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    private Integer version;
}
