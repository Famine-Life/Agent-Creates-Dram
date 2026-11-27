package com.aigc.video.billing;

import com.aigc.video.common.PaymentRequiredException;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.member.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 生成计费：先冻结、成功实扣、失败退回。 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillingProperties properties;
    private final WalletService walletService;
    private final QuotaService quotaService;
    private final MembershipService membershipService;
    private final AppUserMapper userMapper;

    public int videoCost(String modelKey, Integer duration) {
        int base = properties.getVideoCosts().getOrDefault(modelKey, properties.getFallbackVideoCost());
        int seconds = duration == null || duration <= 0 ? 5 : duration;
        return Math.max(1, (int) Math.ceil(base * seconds / 5.0));
    }

    public int imageCost(String modelKey, int count) {
        int base = properties.getImageCosts().getOrDefault(modelKey, properties.getFallbackImageCost());
        return Math.max(1, base * Math.max(1, count));
    }

    public int textCost() {
        return Math.max(0, properties.getTextCost());
    }

    /** 生成前统一校验：邮箱已验证、账号可用、额度足够、余额可冻结。 */
    @Transactional
    public BillingTicket reserve(Long userId, String modelKey, int cost, String bizId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new PaymentRequiredException("ACCOUNT_NOT_FOUND", "账号不存在");
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new PaymentRequiredException("EMAIL_NOT_VERIFIED", "请先完成邮箱验证后再生成");
        }
        if (!user.isActive()) {
            throw new PaymentRequiredException("ACCOUNT_DISABLED", "账号已被禁用");
        }
        MemberPlan plan = membershipService.resolve(user);
        quotaService.assertAllowed(userId, cost, plan);
        if (!walletService.reserve(userId, bizId, cost)) {
            throw new PaymentRequiredException("INSUFFICIENT_BALANCE", "积分不足，请先充值");
        }
        return new BillingTicket(bizId, cost, modelKey);
    }

    public String newOperationId(Long aggregateId) {
        return "gen-" + aggregateId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void settleSuccess(Long userId, String operationId, int credits) {
        if (userId == null || operationId == null) {
            return;
        }
        walletService.capture(userId, operationId);
        // 只有成功生成才计入当日额度，失败重试不重复占用额度
        quotaService.record(userId, Math.max(0, credits));
    }

    public void settleFailure(Long userId, String operationId, String note) {
        if (userId == null || operationId == null) {
            return;
        }
        walletService.release(userId, operationId, note == null ? "生成失败，退回预留积分" : note);
    }

    public record BillingTicket(String operationId, int cost, String modelKey) {
    }
}
