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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String channel;

    private Boolean signatureValid;

    private String result;

    private String payloadHash;

    private LocalDateTime createdAt;
}
