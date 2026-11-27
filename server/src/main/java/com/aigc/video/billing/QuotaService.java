package com.aigc.video.billing;

import com.aigc.video.common.PaymentRequiredException;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.UsageCounter;
import com.aigc.video.mapper.UsageCounterMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 日/小时额度与调用统计。 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final UsageCounterMapper usageMapper;

    /** 今日日期键，格式 yyyyMMdd。 */
    public String today() {
        return LocalDate.now().format(DAY);
    }

    /** 当前小时键，格式 yyyyMMddHH。 */
    public String thisHour() {
        LocalDateTime now = LocalDateTime.now();
        return now.toLocalDate().format(DAY) + String.format("%02d", now.getHour());
    }

    /** 查询用户今日的用量计数，不存在返回 null。 */
    public UsageCounter todayCounter(Long userId) {
        return usageMapper.selectOne(Wrappers.<UsageCounter>lambdaQuery()
                .eq(UsageCounter::getUserId, userId)
                .eq(UsageCounter::getPeriodType, UsageCounter.PERIOD_DAY)
                .eq(UsageCounter::getPeriodKey, today()).last("LIMIT 1"));
    }

    /** 用户今日已消耗的积分。 */
    public int todayCredits(Long userId) {
        UsageCounter counter = todayCounter(userId);
        return counter == null || counter.getCredits() == null ? 0 : counter.getCredits();
    }

    /** 生成前校验当日额度；额度为 0 表示不限。 */
    public void assertAllowed(Long userId, int credits, MemberPlan plan) {
        int limit = plan.getDailyCredits() == null ? 0 : plan.getDailyCredits();
        if (limit <= 0) {
            return;
        }
        if (todayCredits(userId) + credits > limit) {
            throw new PaymentRequiredException("QUOTA_EXCEEDED",
                    "今日积分额度已用完（" + limit + "），请升级会员或明日再试");
        }
    }

    /** 累加当日调用次数与积分，无记录时补建并忽略并发唯一键冲突。 */
    public void record(Long userId, int credits) {
        usageMapper.update(null, Wrappers.<UsageCounter>lambdaUpdate()
                .eq(UsageCounter::getUserId, userId)
                .eq(UsageCounter::getPeriodType, UsageCounter.PERIOD_DAY)
                .eq(UsageCounter::getPeriodKey, today())
                .setSql("calls = calls + 1")
                .setSql("credits = credits + " + Math.max(0, credits))
                .set(UsageCounter::getUpdatedAt, LocalDateTime.now()));
        UsageCounter counter = todayCounter(userId);
        if (counter == null) {
            UsageCounter created = new UsageCounter();
            created.setUserId(userId);
            created.setPeriodType(UsageCounter.PERIOD_DAY);
            created.setPeriodKey(today());
            created.setCalls(1);
            created.setCredits(Math.max(0, credits));
            created.setUpdatedAt(LocalDateTime.now());
            try {
                usageMapper.insert(created);
            } catch (DuplicateKeyException ignored) {
                // 并发插入时由另一线程完成计数
            }
        }
    }
}
