package com.aigc.video.billing;

import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.UsageCounter;
import com.aigc.video.domain.Wallet;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.CreditLedgerMapper;
import com.aigc.video.member.MembershipService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 账户摘要、流水与用量查询。 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AppUserMapper userMapper;
    private final CreditLedgerMapper ledgerMapper;
    private final WalletService walletService;
    private final MembershipService membershipService;
    private final QuotaService quotaService;

    /** 汇总用户会员、钱包与当日用量。 */
    public AccountSummary summary(Long userId) {
        AppUser user = userMapper.selectById(userId);
        MemberPlan plan = membershipService.resolve(user);
        Wallet wallet = walletService.walletOf(userId);
        UsageCounter counter = quotaService.todayCounter(userId);
        return AccountSummary.of(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                Boolean.TRUE.equals(user.getEmailVerified()), plan, user.getMemberExpiresAt(),
                wallet.getAvailableCredits() == null ? 0 : wallet.getAvailableCredits(),
                wallet.getFrozenCredits() == null ? 0 : wallet.getFrozenCredits(),
                counter == null || counter.getCredits() == null ? 0 : counter.getCredits(),
                counter == null || counter.getCalls() == null ? 0 : counter.getCalls());
    }

    /** 查询用户最近的积分流水，limit 最大 200。 */
    public List<CreditLedger> ledgers(Long userId, int limit) {
        return ledgerMapper.selectList(Wrappers.<CreditLedger>lambdaQuery()
                .eq(CreditLedger::getUserId, userId)
                .orderByDesc(CreditLedger::getId)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
    }
}
