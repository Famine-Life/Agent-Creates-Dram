package com.aigc.video.billing;

import com.aigc.video.domain.MemberPlan;

import java.time.LocalDateTime;

/** 账户摘要：前端顶栏与“我的账户”共用。 */
public record AccountSummary(
        Long id,
        String username,
        String email,
        String role,
        boolean emailVerified,
        String memberCode,
        String memberName,
        LocalDateTime memberExpiresAt,
        int dailyCredits,
        int maxConcurrent,
        String maxResolution,
        int availableCredits,
        int frozenCredits,
        int todayCredits,
        int todayCalls
) {

    public static AccountSummary of(Long id, String username, String email, String role,
                                    boolean verified, MemberPlan plan, LocalDateTime expiresAt,
                                    int available, int frozen, int todayCredits, int todayCalls) {
        return new AccountSummary(id, username, email, role, verified,
                plan.getCode(), plan.getName(), expiresAt,
                plan.getDailyCredits() == null ? 0 : plan.getDailyCredits(),
                plan.getMaxConcurrent() == null ? 1 : plan.getMaxConcurrent(),
                plan.getMaxResolution(),
                available, frozen, todayCredits, todayCalls);
    }
}
