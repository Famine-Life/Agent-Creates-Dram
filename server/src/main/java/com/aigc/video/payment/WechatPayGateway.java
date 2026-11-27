package com.aigc.video.payment;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.domain.PaymentOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** 微信支付 V3 Native 下单与回调验签。 */
@Component
@RequiredArgsConstructor
public class WechatPayGateway {

    private static final String API = "https://api.mch.weixin.qq.com";

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public boolean enabled() {
        PaymentProperties.Wechat wx = properties.getWechat();
        return notBlank(wx.getAppId()) && notBlank(wx.getMchId()) && notBlank(wx.getSerialNo())
                && notBlank(wx.getPrivateKeyPath()) && notBlank(wx.getNotifyUrl());
    }

    public String createNative(PaymentOrder order) {
        PaymentProperties.Wechat wx = properties.getWechat();
        requireEnabled();
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "appid", wx.getAppId(),
                    "mchid", wx.getMchId(),
                    "description", "Agent造剧-" + order.getProductCode(),
                    "out_trade_no", order.getOrderNo(),
                    "notify_url", wx.getNotifyUrl(),
                    "amount", Map.of("total", order.getAmountCents(), "currency", "CNY")));
        } catch (Exception e) {
            throw new BadRequestException("微信支付下单参数序列化失败");
        }
        String path = "/v3/pay/transactions/native";
        String url = API + path;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String message = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = sign(message, loadPrivateKey(wx.getPrivateKeyPath()));
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wx.getMchId()
                + "\",nonce_str=\"" + nonce + "\",signature=\"" + signature
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + wx.getSerialNo() + "\"";
        JsonNode response;
        try {
            response = RestClient.create().post()
                    .uri(url)
                    .header("Authorization", authorization)
                    .header("Accept", "application/json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new BadRequestException("微信支付下单失败：" + e.getMessage());
        }
        String codeUrl = response == null ? "" : response.path("code_url").asText("");
        if (codeUrl.isBlank()) {
            throw new BadRequestException("微信支付未返回二维码地址");
        }
        return codeUrl;
    }

    /** 验签并解密回调，返回订单号、微信交易号与金额。 */
    public NotifyResult parseNotify(Map<String, String> headers, String body) {
        PaymentProperties.Wechat wx = properties.getWechat();
        requireEnabled();
        String timestamp = header(headers, "Wechatpay-Timestamp");
        String nonce = header(headers, "Wechatpay-Nonce");
        String signature = header(headers, "Wechatpay-Signature");
        if (timestamp == null || nonce == null || signature == null) {
            throw new BadRequestException("微信回调缺少签名头");
        }
        verifySignature(timestamp + "\n" + nonce + "\n" + body + "\n", signature, loadPlatformPublicKey(wx));
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.path("resource");
            String plain = decryptResource(resource, wx.getApiV3Key());
            JsonNode data = objectMapper.readTree(plain);
            return new NotifyResult(data.path("out_trade_no").asText(),
                    data.path("transaction_id").asText(),
                    data.path("amount").path("total").asInt(),
                    "SUCCESS".equalsIgnoreCase(data.path("trade_state").asText()));
        } catch (Exception e) {
            throw new BadRequestException("微信回调报文解析失败：" + e.getMessage());
        }
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BadRequestException("微信支付未配置，请设置 WECHAT_PAY_* 环境变量");
        }
    }

    private String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String decryptResource(JsonNode resource, String apiV3Key) throws Exception {
        String ciphertext = resource.path("ciphertext").asText();
        String nonce = resource.path("nonce").asText();
        String associated = resource.path("associated_data").asText("");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        if (!associated.isEmpty()) {
            cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        }
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private String sign(String message, PrivateKey key) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("微信支付签名失败", e);
        }
    }

    private void verifySignature(String message, String signature, PublicKey key) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                throw new BadRequestException("微信回调验签失败");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("微信回调验签失败");
        }
    }

    private PrivateKey loadPrivateKey(String path) {
        try {
            String pem = Files.readString(Path.of(path));
            String base64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("读取微信支付私钥失败：" + path, e);
        }
    }

    private PublicKey loadPlatformPublicKey(PaymentProperties.Wechat wx) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                    Files.newInputStream(Path.of(wx.getPlatformCertPath())));
            return certificate.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("读取微信支付平台证书失败", e);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record NotifyResult(String orderNo, String thirdPartyTradeNo, int amountCents, boolean success) {
    }
}
