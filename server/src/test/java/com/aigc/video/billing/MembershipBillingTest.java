package com.aigc.video.billing;

import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.PaymentOrder;
import com.aigc.video.domain.Wallet;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.member.MembershipService;
import com.aigc.video.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:membership-billing-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "aigc.storage.type=local",
        "aigc.payment.channel=mock"
})
class MembershipBillingTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AppUserMapper userMapper;
    @Autowired
    private MembershipService membershipService;

    @Test
    void reserveCaptureAndReleaseAreIdempotent() {
        Long userId = newUser("wallet-user");
        walletService.credit(userId, CreditLedger.TYPE_ADJUST, "test-init", 100, "测试初始积分");

        assertTrue(walletService.reserve(userId, "biz-1", 30));
        // 重复预留不会二次冻结
        assertTrue(walletService.reserve(userId, "biz-1", 30));
        Wallet afterReserve = walletService.walletOf(userId);
        assertEquals(70, afterReserve.getAvailableCredits());
        assertEquals(30, afterReserve.getFrozenCredits());

        walletService.capture(userId, "biz-1");
        walletService.capture(userId, "biz-1");
        Wallet afterCapture = walletService.walletOf(userId);
        assertEquals(70, afterCapture.getAvailableCredits());
        assertEquals(0, afterCapture.getFrozenCredits());

        assertTrue(walletService.reserve(userId, "biz-2", 20));
        walletService.release(userId, "biz-2", "测试退回");
        walletService.release(userId, "biz-2", "重复退回");
        Wallet afterRelease = walletService.walletOf(userId);
        assertEquals(70, afterRelease.getAvailableCredits());
        assertEquals(0, afterRelease.getFrozenCredits());

        assertFalse(walletService.reserve(userId, "biz-3", 9999));
        assertEquals(70, walletService.walletOf(userId).getAvailableCredits());
    }

    @Test
    void mockPaymentGrantsCreditsOnlyOnce() {
        Long userId = newUser("pay-user");
        Wallet before = walletService.walletOf(userId);

        PaymentService.PayInfo info = paymentService.createOrder(userId, PaymentOrder.TYPE_CREDITS,
                "CREDITS_100", "mock");
        assertTrue(info.mock);
        assertEquals(PaymentOrder.STATUS_PENDING, info.status);

        paymentService.mockPay(userId, info.orderNo);
        paymentService.mockPay(userId, info.orderNo);

        Wallet after = walletService.walletOf(userId);
        assertEquals(before.getAvailableCredits() + 100, after.getAvailableCredits());
        assertEquals(PaymentOrder.STATUS_PAID,
                paymentService.getOrder(userId, info.orderNo).getStatus());
    }

    @Test
    void mockMembershipOrderUpgradesUser() {
        Long userId = newUser("member-user");
        PaymentService.PayInfo info = paymentService.createOrder(userId, PaymentOrder.TYPE_MEMBERSHIP,
                MemberPlan.PRO, "mock");
        paymentService.mockPay(userId, info.orderNo);

        AppUser user = userMapper.selectById(userId);
        assertEquals(MemberPlan.PRO, user.getMemberCode());
        assertNotNull(user.getMemberExpiresAt());
        assertTrue(user.getMemberExpiresAt().isAfter(LocalDateTime.now()));
        assertEquals(MemberPlan.PRO, membershipService.resolve(user).getCode());
    }

    private Long newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser user = new AppUser();
        user.setUsername(prefix + "-" + suffix);
        user.setUsernameLower(user.getUsername().toLowerCase());
        user.setEmail(prefix + "-" + suffix + "@example.test");
        user.setEmailLower(user.getEmail().toLowerCase());
        user.setPasswordHash("x");
        user.setRole(AppUser.ROLE_USER);
        user.setStatus(AppUser.STATUS_ACTIVE);
        user.setEmailVerified(true);
        user.setMemberCode(MemberPlan.FREE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        walletService.ensureWallet(user.getId());
        return user.getId();
    }
}
