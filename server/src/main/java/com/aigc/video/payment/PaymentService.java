package com.aigc.video.payment;

import com.aigc.video.audit.AuditService;
import com.aigc.video.billing.WalletService;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.CreditPackage;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.PaymentNotifyLog;
import com.aigc.video.domain.PaymentOrder;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.CreditPackageMapper;
import com.aigc.video.mapper.PaymentNotifyLogMapper;
import com.aigc.video.mapper.PaymentOrderMapper;
import com.aigc.video.member.MembershipService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** 订单状态机与到账：验签通过后 CAS 更新订单，再在同一事务入账。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentOrderMapper orderMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final CreditPackageMapper packageMapper;
    private final AppUserMapper userMapper;
    private final MembershipService membershipService;
    private final WalletService walletService;
    private final AuditService auditService;
    private final PaymentProperties properties;
    private final WechatPayGateway wechatPayGateway;
    private final AlipayGateway alipayGateway;
    private final ObjectMapper objectMapper;

    /** 创建积分/会员订单，并按渠道返回 Mock 标记、微信二维码或支付宝表单。 */
    @Transactional
    public PayInfo createOrder(Long userId, String productType, String productCode, String channel) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("请先完成邮箱验证后再充值");
        }
        String resolvedChannel = channel == null || channel.isBlank() ? properties.getChannel() : channel.trim();
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo());
        order.setUserId(userId);
        order.setChannel(resolvedChannel);
        order.setStatus(PaymentOrder.STATUS_PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setVersion(0);
        order.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(5, properties.getOrderTimeoutMinutes())));

        if (PaymentOrder.TYPE_CREDITS.equalsIgnoreCase(productType)) {
            CreditPackage item = packageMapper.selectOne(Wrappers.<CreditPackage>lambdaQuery()
                    .eq(CreditPackage::getCode, productCode).last("LIMIT 1"));
            if (item == null || !Boolean.TRUE.equals(item.getEnabled())) {
                throw new BadRequestException("积分商品不存在或已下架");
            }
            order.setProductType(PaymentOrder.TYPE_CREDITS);
            order.setProductCode(item.getCode());
            order.setAmountCents(item.getPriceCents());
            order.setCredits(item.getCredits());
            order.setProductSnapshot(snapshot(Map.of("name", item.getName(), "credits", item.getCredits(),
                    "priceCents", item.getPriceCents())));
        } else if (PaymentOrder.TYPE_MEMBERSHIP.equalsIgnoreCase(productType)) {
            MemberPlan plan = membershipService.require(productCode);
            if (!Boolean.TRUE.equals(plan.getEnabled())) {
                throw new BadRequestException("会员档位已下架");
            }
            order.setProductType(PaymentOrder.TYPE_MEMBERSHIP);
            order.setProductCode(plan.getCode());
            order.setPlanCode(plan.getCode());
            order.setAmountCents(plan.getPriceCents());
            order.setProductSnapshot(snapshot(Map.of("name", plan.getName(), "days", plan.getDurationDays(),
                    "priceCents", plan.getPriceCents())));
        } else {
            throw new BadRequestException("不支持的商品类型：" + productType);
        }

        if (order.getAmountCents() == null || order.getAmountCents() <= 0) {
            throw new BadRequestException("该商品未配置价格");
        }
        orderMapper.insert(order);

        PayInfo info = new PayInfo();
        info.orderNo = order.getOrderNo();
        info.status = order.getStatus();
        info.channel = resolvedChannel;
        info.amountCents = order.getAmountCents();
        info.expiresAt = order.getExpiresAt();
        switch (resolvedChannel) {
            case "mock" -> {
                if (!properties.isMockEnabled()) {
                    throw new BadRequestException("Mock 支付未开启");
                }
                info.mock = true;
            }
            case "wechat" -> info.codeUrl = wechatPayGateway.createNative(order);
            case "alipay" -> info.formHtml = alipayGateway.pagePay(order);
            default -> throw new BadRequestException("不支持的支付渠道：" + resolvedChannel);
        }
        auditService.record(userId, "ORDER_CREATE", "payment_order", order.getOrderNo(), resolvedChannel);
        return info;
    }

    /** 当前用户最近的订单，limit 最大 100。 */
    public List<PaymentOrder> listOrders(Long userId, int limit) {
        return orderMapper.selectList(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getUserId, userId)
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT " + Math.max(1, Math.min(100, limit))));
    }

    /** 全部订单，供后台查看，limit 最大 200。 */
    public List<PaymentOrder> listAllOrders(int limit) {
        return orderMapper.selectList(Wrappers.<PaymentOrder>lambdaQuery()
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
    }

    /** 查询当前用户的订单，不存在时抛 404。 */
    public PaymentOrder getOrder(Long userId, String orderNo) {
        PaymentOrder order = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getUserId, userId)
                .last("LIMIT 1"));
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        return order;
    }

    /** Mock 渠道模拟支付，复用统一的到账逻辑。 */
    @Transactional
    public PaymentOrder mockPay(Long userId, String orderNo) {
        PaymentOrder order = getOrder(userId, orderNo);
        if (!"mock".equals(order.getChannel()) || !properties.isMockEnabled()) {
            throw new BadRequestException("当前订单不支持 Mock 支付");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("订单已过期");
        }
        handlePaid(orderNo, "mock", order.getAmountCents(), "MOCK-" + orderNo);
        return getOrder(userId, orderNo);
    }

    /** 关闭未支付订单，已支付订单不允许关闭。 */
    @Transactional
    public PaymentOrder close(Long userId, String orderNo) {
        PaymentOrder order = getOrder(userId, orderNo);
        if (PaymentOrder.STATUS_PAID.equals(order.getStatus())) {
            throw new BadRequestException("订单已支付，无法关闭");
        }
        orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .eq(PaymentOrder::getId, order.getId())
                .in(PaymentOrder::getStatus, PaymentOrder.STATUS_PENDING, PaymentOrder.STATUS_PAYING)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CLOSED)
                .set(PaymentOrder::getUpdatedAt, LocalDateTime.now()));
        return getOrder(userId, orderNo);
    }

    /** 回调/支付完成统一入口：校验金额后用 CAS 更新订单并入账，重复通知只入账一次。 */
    @Transactional
    public String handlePaid(String orderNo, String channel, int amountCents, String thirdPartyTradeNo) {
        PaymentOrder order = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getOrderNo, orderNo).last("LIMIT 1"));
        if (order == null) {
            return "ORDER_NOT_FOUND";
        }
        if (PaymentOrder.STATUS_PAID.equals(order.getStatus())) {
            return "DUPLICATE";
        }
        if (order.getAmountCents() == null || order.getAmountCents() != amountCents) {
            logNotify(orderNo, channel, true, "AMOUNT_MISMATCH");
            return "AMOUNT_MISMATCH";
        }
        if (PaymentOrder.STATUS_CLOSED.equals(order.getStatus())) {
            logNotify(orderNo, channel, true, "CLOSED_AFTER_PAY");
            return "CLOSED_AFTER_PAY";
        }
        int updated = orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .eq(PaymentOrder::getId, order.getId())
                .in(PaymentOrder::getStatus, PaymentOrder.STATUS_PENDING, PaymentOrder.STATUS_PAYING)
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID)
                .set(PaymentOrder::getChannel, channel)
                .set(PaymentOrder::getThirdPartyTradeNo, thirdPartyTradeNo)
                .set(PaymentOrder::getPaidAt, LocalDateTime.now())
                .set(PaymentOrder::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated == 0) {
            PaymentOrder latest = orderMapper.selectById(order.getId());
            return latest != null && PaymentOrder.STATUS_PAID.equals(latest.getStatus()) ? "DUPLICATE" : "CONFLICT";
        }

        if (PaymentOrder.TYPE_CREDITS.equals(order.getProductType())) {
            walletService.credit(order.getUserId(), CreditLedger.TYPE_RECHARGE, order.getOrderNo(),
                    order.getCredits() == null ? 0 : order.getCredits(), "积分充值到账");
        } else if (PaymentOrder.TYPE_MEMBERSHIP.equals(order.getProductType())) {
            MemberPlan plan = membershipService.require(order.getPlanCode());
            membershipService.upgrade(order.getUserId(), plan.getCode(),
                    plan.getDurationDays() == null ? 30 : plan.getDurationDays());
        }
        logNotify(orderNo, channel, true, "SUCCESS");
        auditService.record(order.getUserId(), "ORDER_PAID", "payment_order", orderNo,
                "amount=" + amountCents + ",channel=" + channel);
        return "SUCCESS";
    }

    /** 记录异步通知的验签与处理结果。 */
    public void logNotify(String orderNo, String channel, boolean signatureValid, String result) {
        PaymentNotifyLog entry = new PaymentNotifyLog();
        entry.setOrderNo(orderNo);
        entry.setChannel(channel);
        entry.setSignatureValid(signatureValid);
        entry.setResult(result);
        entry.setCreatedAt(LocalDateTime.now());
        notifyLogMapper.insert(entry);
    }

    /** 定时关闭超时未支付的订单。 */
    @Scheduled(fixedDelayString = "${aigc.payment.expire-interval-ms:60000}")
    public void closeExpired() {
        orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .in(PaymentOrder::getStatus, PaymentOrder.STATUS_PENDING, PaymentOrder.STATUS_PAYING)
                .lt(PaymentOrder::getExpiresAt, LocalDateTime.now())
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CLOSED)
                .set(PaymentOrder::getUpdatedAt, LocalDateTime.now()));
    }

    private String orderNo() {
        return "AG" + LocalDateTime.now().format(ORDER_TIME)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private String snapshot(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 下单返回给前端的支付信息。 */
    public static class PayInfo {
        /** 平台订单号 */
        public String orderNo;
        /** 订单状态 */
        public String status;
        /** 支付渠道 */
        public String channel;
        /** 应付金额（分） */
        public int amountCents;
        /** 是否为 Mock 支付 */
        public boolean mock;
        /** 微信 Native 支付二维码链接 */
        public String codeUrl;
        /** 支付宝支付表单 HTML */
        public String formHtml;
        /** 订单过期时间 */
        public LocalDateTime expiresAt;

        /** 转为前端可直接消费的 Map。 */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("orderNo", orderNo);
            map.put("status", status);
            map.put("channel", channel);
            map.put("amountCents", amountCents);
            map.put("mock", mock);
            map.put("codeUrl", codeUrl);
            map.put("formHtml", formHtml);
            map.put("expiresAt", expiresAt == null ? "" : expiresAt.toString());
            return map;
        }
    }
}
