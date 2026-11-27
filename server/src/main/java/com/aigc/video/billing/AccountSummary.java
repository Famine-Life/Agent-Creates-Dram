package com.aigc.video.billing;

import com.aigc.video.domain.MemberPlan;

import java.time.LocalDateTime;

/** 账户摘要：前端顶栏与“我的账户”共用。 */
public record AccountSummary(
        /** 用户 ID */
        Long id,
        /** 用户名 */
        String username,
        /** 邮箱 */
        String email,
        /** 角色 */
        String role,
        /** 邮箱是否已验证 */
        boolean emailVerified,
        /** 生效会员档位编码 */
        String memberCode,
        /** 会员档位名称 */
        String memberName,
        /** 会员到期时间 */
        LocalDateTime memberExpiresAt,
        /** 每日积分额度，0 表示不限 */
        int dailyCredits,
        /** 最大并发任务数 */
        int maxConcurrent,
        /** 最高可用清晰度 */
        String maxResolution,
        /** 可用积分 */
        int availableCredits,
        /** 冻结积分 */
        int frozenCredits,
        /** 今日已用积分 */
        int todayCredits,
        /** 今日调用次数 */
        int todayCalls
) {

    /** 由用户、会员档位与钱包用量组装摘要。 */
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
