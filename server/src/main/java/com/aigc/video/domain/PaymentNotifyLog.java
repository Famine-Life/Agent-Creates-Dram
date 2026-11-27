package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付回调日志，不记录密钥与完整报文。 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台订单号 */
    private String orderNo;

    /** 支付渠道：wechat、alipay 或 mock */
    private String channel;

    /** 验签是否通过 */
    private Boolean signatureValid;

    /** 处理结果，如 SUCCESS、AMOUNT_MISMATCH */
    private String result;

    /** 报文摘要，避免明文存储敏感内容 */
    private String payloadHash;

    /** 记录时间 */
    private LocalDateTime createdAt;
}
