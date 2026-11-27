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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String productType;

    private String productCode;

    private String productSnapshot;

    private Integer amountCents;

    private Integer credits;

    private String planCode;

    private String status;

    private String channel;

    private String thirdPartyTradeNo;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
