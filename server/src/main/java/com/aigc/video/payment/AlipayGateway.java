package com.aigc.video.payment;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.domain.PaymentOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 支付宝电脑网站支付：表单签名与异步通知验签。 */
@Component
@RequiredArgsConstructor
public class AlipayGateway {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    /** 应用配置齐全时视为启用支付宝渠道。 */
    public boolean enabled() {
        PaymentProperties.Alipay ali = properties.getAlipay();
        return notBlank(ali.getAppId()) && notBlank(ali.getPrivateKey())
                && notBlank(ali.getAlipayPublicKey()) && notBlank(ali.getNotifyUrl());
    }

    /** 构造并签名自动提交的电脑网站支付表单 HTML。 */
    public String pagePay(PaymentOrder order) {
        PaymentProperties.Alipay ali = properties.getAlipay();
        requireEnabled();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", ali.getAppId());
        params.put("method", "alipay.trade.page.pay");
        params.put("format", "JSON");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        params.put("timestamp", LocalDateTime.now().format(TIME));
        params.put("version", "1.0");
        params.put("notify_url", ali.getNotifyUrl());
        if (notBlank(ali.getReturnUrl())) {
            params.put("return_url", ali.getReturnUrl());
        }
        try {
            String biz = objectMapper.writeValueAsString(Map.of(
                    "out_trade_no", order.getOrderNo(),
                    "total_amount", String.format("%.2f", order.getAmountCents() / 100.0),
                    "subject", "Agent造剧-" + order.getProductCode(),
                    "product_code", "FAST_INSTANT_TRADE_PAY"));
            params.put("biz_content", biz);
        } catch (Exception e) {
            throw new BadRequestException("支付宝下单参数序列化失败");
        }
        params.put("sign", sign(params, loadPrivateKey(ali.getPrivateKey())));

        StringBuilder html = new StringBuilder();
        html.append("<form id=\"alipay\" method=\"post\" action=\"").append(ali.getGateway()).append("\">");
        params.forEach((key, value) -> html.append("<input type=\"hidden\" name=\"")
                .append(escape(key)).append("\" value=\"").append(escape(value)).append("\"/>"));
        html.append("</form><script>document.getElementById('alipay').submit();</script>");
        return html.toString();
    }

    /** 校验异步通知签名并解析订单号、交易号、金额与支付状态。 */
    public NotifyResult parseNotify(Map<String, String> params) {
        PaymentProperties.Alipay ali = properties.getAlipay();
        requireEnabled();
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            throw new BadRequestException("支付宝回调缺少签名");
        }
        Map<String, String> signParams = new TreeMap<>(params);
        signParams.remove("sign");
        signParams.remove("sign_type");
        if (!verify(signContent(signParams), sign, loadPublicKey(ali.getAlipayPublicKey()))) {
            throw new BadRequestException("支付宝回调验签失败");
        }
        String amount = params.getOrDefault("total_amount", "0");
        int cents;
        try {
            cents = (int) Math.round(Double.parseDouble(amount) * 100);
        } catch (NumberFormatException e) {
            cents = -1;
        }
        String status = params.getOrDefault("trade_status", "");
        boolean success = "TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status);
        return new NotifyResult(params.getOrDefault("out_trade_no", ""),
                params.getOrDefault("trade_no", ""), cents, success);
    }

    private String sign(Map<String, String> params, PrivateKey key) {
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.remove("sign");
        sorted.remove("sign_type");
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signContent(sorted).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("支付宝签名失败", e);
        }
    }

    private boolean verify(String content, String sign, PublicKey key) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            return false;
        }
    }

    private String signContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private PrivateKey loadPrivateKey(String value) {
        try {
            String base64 = value.contains("BEGIN")
                    ? value.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "")
                    : value.replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("读取支付宝应用私钥失败", e);
        }
    }

    private PublicKey loadPublicKey(String value) {
        try {
            String base64 = value.contains("BEGIN")
                    ? value.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "")
                    : value.replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("读取支付宝公钥失败", e);
        }
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BadRequestException("支付宝支付未配置，请设置 ALIPAY_* 环境变量");
        }
    }

    private String escape(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 异步通知解析结果。 */
    public record NotifyResult(String orderNo, String thirdPartyTradeNo, int amountCents, boolean success) {
    }
}
