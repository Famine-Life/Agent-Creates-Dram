package com.aigc.video.member;

import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.MemberPlanMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 会员档位、权益解析与默认播种。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private static final List<String> RESOLUTION_ORDER = List.of("480P", "720P", "1080P", "4K");

    private final MemberPlanMapper planMapper;
    private final AppUserMapper userMapper;

    public List<MemberPlan> listEnabled() {
        return planMapper.selectList(Wrappers.<MemberPlan>lambdaQuery()
                .eq(MemberPlan::getEnabled, true)
                .orderByAsc(MemberPlan::getSortOrder)
                .orderByAsc(MemberPlan::getId));
    }

    public List<MemberPlan> listAll() {
        return planMapper.selectList(Wrappers.<MemberPlan>lambdaQuery()
                .orderByAsc(MemberPlan::getSortOrder));
    }

    public MemberPlan require(String code) {
        MemberPlan plan = code == null ? null : planMapper.selectOne(Wrappers.<MemberPlan>lambdaQuery()
                .eq(MemberPlan::getCode, code).last("LIMIT 1"));
        if (plan == null) {
            throw new IllegalArgumentException("会员档位不存在：" + code);
        }
        return plan;
    }

    /** 解析用户当前生效的会员；过期自动回落到免费档。 */
    public MemberPlan resolve(AppUser user) {
        String code = user == null ? null : user.getMemberCode();
        if (code == null || code.isBlank()) {
            code = MemberPlan.FREE;
        }
        if (!MemberPlan.FREE.equals(code) && user.getMemberExpiresAt() != null
                && user.getMemberExpiresAt().isBefore(LocalDateTime.now())) {
            code = MemberPlan.FREE;
        }
        MemberPlan plan = planMapper.selectOne(Wrappers.<MemberPlan>lambdaQuery()
                .eq(MemberPlan::getCode, code).last("LIMIT 1"));
        if (plan == null) {
            plan = planMapper.selectOne(Wrappers.<MemberPlan>lambdaQuery()
                    .eq(MemberPlan::getCode, MemberPlan.FREE).last("LIMIT 1"));
        }
        if (plan == null) {
            // 极端情况下迁移未执行，返回内存兜底，避免登录后无法使用
            plan = new MemberPlan();
            plan.setCode(MemberPlan.FREE);
            plan.setName("免费版");
            plan.setDailyCredits(50);
            plan.setMaxConcurrent(1);
            plan.setMaxResolution("720P");
            plan.setDispatchWeight(1);
            plan.setEnabled(true);
        }
        return plan;
    }

    public MemberPlan resolveByUserId(Long userId) {
        AppUser user = userId == null ? null : userMapper.selectById(userId);
        return resolve(user);
    }

    /** 按会员权益限制清晰度，超出档位自动回落到允许的最高档。 */
    public String clampResolution(Long userId, String resolution) {
        MemberPlan plan = resolveByUserId(userId);
        int allowed = rank(plan.getMaxResolution());
        int requested = rank(resolution);
        if (requested <= allowed) {
            return resolution;
        }
        return RESOLUTION_ORDER.get(Math.max(0, allowed));
    }

    private static int rank(String resolution) {
        int index = RESOLUTION_ORDER.indexOf(resolution);
        return index < 0 ? 1 : index;
    }

    @Transactional
    public void upgrade(Long userId, String planCode, int days) {
        MemberPlan plan = require(planCode);
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        LocalDateTime base = user.getMemberExpiresAt() != null
                && user.getMemberExpiresAt().isAfter(LocalDateTime.now())
                ? user.getMemberExpiresAt() : LocalDateTime.now();
        userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                .eq(AppUser::getId, userId)
                .set(AppUser::getMemberCode, plan.getCode())
                .set(AppUser::getMemberExpiresAt, base.plusDays(Math.max(1, days)))
                .set(AppUser::getUpdatedAt, LocalDateTime.now()));
    }

    /** 首次启动播种三档会员与积分商品；已存在则保留后台修改。 */
    @Transactional
    public void seedDefaults() {
        seedPlan(MemberPlan.FREE, "免费版", "体验画布与基础模型，每日赠送积分", 0, 30, 50, 1, "720P", 1, 0);
        seedPlan(MemberPlan.PRO, "专业版", "更高日额度、双并发与 1080P", 2990, 30, 500, 2, "1080P", 3, 1);
        seedPlan(MemberPlan.STUDIO, "工作室版", "最高额度、5 并发与 4K 权益", 9990, 30, 3000, 5, "4K", 6, 2);
    }

    private void seedPlan(String code, String name, String description, int priceCents, int days,
                          int dailyCredits, int maxConcurrent, String maxResolution,
                          int weight, int sort) {
        Long count = planMapper.selectCount(Wrappers.<MemberPlan>lambdaQuery().eq(MemberPlan::getCode, code));
        if (count != null && count > 0) {
            return;
        }
        MemberPlan plan = new MemberPlan();
        plan.setCode(code);
        plan.setName(name);
        plan.setDescription(description);
        plan.setPriceCents(priceCents);
        plan.setDurationDays(days);
        plan.setDailyCredits(dailyCredits);
        plan.setMaxConcurrent(maxConcurrent);
        plan.setMaxResolution(maxResolution);
        plan.setDispatchWeight(weight);
        plan.setEnabled(true);
        plan.setSortOrder(sort);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        log.info("已播种会员档位 {}", code);
    }
}
