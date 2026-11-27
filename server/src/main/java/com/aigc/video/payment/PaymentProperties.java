package com.aigc.video.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付渠道配置；密钥均来自环境变量。 */
@Data
@Component
@ConfigurationProperties(prefix = "aigc.payment")
public class PaymentProperties {

    /** 默认支付渠道：mock、wechat 或 alipay */
    private String channel = "mock";

    /** 是否允许 Mock 支付，仅建议在开发环境开启 */
    private boolean mockEnabled = true;

    /** 订单支付超时时间（分钟） */
    private int orderTimeoutMinutes = 30;

    /** 微信支付配置 */
    private Wechat wechat = new Wechat();

    /** 支付宝配置 */
    private Alipay alipay = new Alipay();

    @Data
    public static class Wechat {
        /** 公众号/应用 AppID */
        private String appId;
        /** 商户号 */
        private String mchId;
        /** 商户 API 证书序列号 */
        private String serialNo;
        /** 商户私钥文件路径 */
        private String privateKeyPath;
        /** APIv3 密钥，用于解密回调资源 */
        private String apiV3Key;
        /** 微信支付平台证书路径，用于验签 */
        private String platformCertPath;
        /** 支付结果异步通知地址 */
        private String notifyUrl;
    }

    @Data
    public static class Alipay {
        /** 支付宝应用 AppID */
        private String appId;
        /** 应用私钥 */
        private String privateKey;
        /** 支付宝公钥，用于验签 */
        private String alipayPublicKey;
        /** 网关地址 */
        private String gateway = "https://openapi.alipay.com/gateway.do";
        /** 异步通知地址 */
        private String notifyUrl;
        /** 同步跳转地址 */
        private String returnUrl;
    }
}
