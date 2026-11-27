package com.aigc.video.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付渠道配置；密钥均来自环境变量。 */
@Data
@Component
@ConfigurationProperties(prefix = "aigc.payment")
public class PaymentProperties {

    private String channel = "mock";

    private boolean mockEnabled = true;

    private int orderTimeoutMinutes = 30;

    private Wechat wechat = new Wechat();

    private Alipay alipay = new Alipay();

    @Data
    public static class Wechat {
        private String appId;
        private String mchId;
        private String serialNo;
        private String privateKeyPath;
        private String apiV3Key;
        private String platformCertPath;
        private String notifyUrl;
    }

    @Data
    public static class Alipay {
        private String appId;
        private String privateKey;
        private String alipayPublicKey;
        private String gateway = "https://openapi.alipay.com/gateway.do";
        private String notifyUrl;
        private String returnUrl;
    }
}
