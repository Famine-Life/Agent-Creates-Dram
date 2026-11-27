package com.aigc.video.api;

import com.aigc.video.common.BadRequestException;
import com.aigc.video.domain.PaymentOrder;
import com.aigc.video.payment.AlipayGateway;
import com.aigc.video.payment.PaymentProperties;
import com.aigc.video.payment.PaymentService;
import com.aigc.video.payment.WechatPayGateway;
import com.aigc.video.user.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 订单、Mock 支付与微信/支付宝异步通知。 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentProperties properties;
    private final WechatPayGateway wechatPayGateway;
    private final AlipayGateway alipayGateway;

    @GetMapping("/api/payments/channels")
    public Map<String, Object> channels() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mock", properties.isMockEnabled());
        result.put("wechat", wechatPayGateway.enabled());
        result.put("alipay", alipayGateway.enabled());
        result.put("default", properties.getChannel());
        return result;
    }

    @PostMapping("/api/payments/orders")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String productType = string(body.get("productType"));
        String productCode = string(body.get("productCode"));
        String channel = string(body.get("channel"));
        return paymentService.createOrder(UserContext.requireUserId(), productType, productCode, channel).toMap();
    }

    @GetMapping("/api/payments/orders")
    public List<PaymentOrder> orders(@RequestParam(defaultValue = "20") int limit) {
        return paymentService.listOrders(UserContext.requireUserId(), limit);
    }

    @GetMapping("/api/payments/orders/{orderNo}")
    public PaymentOrder order(@PathVariable String orderNo) {
        return paymentService.getOrder(UserContext.requireUserId(), orderNo);
    }

    @PostMapping("/api/payments/orders/{orderNo}/mock-pay")
    public PaymentOrder mockPay(@PathVariable String orderNo) {
        return paymentService.mockPay(UserContext.requireUserId(), orderNo);
    }

    @PostMapping("/api/payments/orders/{orderNo}/close")
    public PaymentOrder close(@PathVariable String orderNo) {
        return paymentService.close(UserContext.requireUserId(), orderNo);
    }

    @RequestMapping(value = "/api/payments/notify/wechat", method = {RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> wechatNotify(@RequestHeader Map<String, String> headers,
                                                            @RequestBody String body) {
        try {
            WechatPayGateway.NotifyResult result = wechatPayGateway.parseNotify(headers, body);
            if (!result.success()) {
                paymentService.logNotify(result.orderNo(), "wechat", true, "NOT_SUCCESS");
                return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "已忽略非成功状态"));
            }
            String outcome = paymentService.handlePaid(result.orderNo(), "wechat",
                    result.amountCents(), result.thirdPartyTradeNo());
            boolean ok = "SUCCESS".equals(outcome) || "DUPLICATE".equals(outcome);
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("code", "FAIL", "message", outcome));
            }
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception e) {
            log.warn("微信支付回调处理失败：{}", e.getMessage());
            paymentService.logNotify(null, "wechat", false, "INVALID");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "FAIL", "message", "验签或处理失败"));
        }
    }

    @PostMapping("/api/payments/notify/alipay")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        try {
            AlipayGateway.NotifyResult result = alipayGateway.parseNotify(params);
            if (!result.success()) {
                paymentService.logNotify(result.orderNo(), "alipay", true, "NOT_SUCCESS");
                return "success";
            }
            String outcome = paymentService.handlePaid(result.orderNo(), "alipay",
                    result.amountCents(), result.thirdPartyTradeNo());
            if ("SUCCESS".equals(outcome) || "DUPLICATE".equals(outcome)) {
                return "success";
            }
            return "failure";
        } catch (BadRequestException e) {
            log.warn("支付宝回调处理失败：{}", e.getMessage());
            paymentService.logNotify(params.get("out_trade_no"), "alipay", false, "INVALID");
            return "failure";
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
