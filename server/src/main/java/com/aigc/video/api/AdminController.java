package com.aigc.video.api;

import com.aigc.video.audit.AuditService;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.AuditLog;
import com.aigc.video.domain.CreditPackage;
import com.aigc.video.domain.EventOutbox;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.PaymentOrder;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.AuditLogMapper;
import com.aigc.video.mapper.CreditPackageMapper;
import com.aigc.video.mapper.EventOutboxMapper;
import com.aigc.video.mapper.MemberPlanMapper;
import com.aigc.video.member.MembershipService;
import com.aigc.video.payment.PaymentService;
import com.aigc.video.user.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理后台：用户、会员商品、订单、审计与死信。 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserMapper userMapper;
    private final MemberPlanMapper planMapper;
    private final CreditPackageMapper packageMapper;
    private final AuditLogMapper auditMapper;
    private final EventOutboxMapper outboxMapper;
    private final MembershipService membershipService;
    private final PaymentService paymentService;
    private final AuditService auditService;

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", userMapper.selectCount(null));
        result.put("orders", paymentService.listAllOrders(1).size());
        result.put("deadEvents", outboxMapper.selectCount(Wrappers.<EventOutbox>lambdaQuery()
                .eq(EventOutbox::getStatus, EventOutbox.STATUS_DEAD)));
        return result;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "50") int limit) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase();
        List<AppUser> users = userMapper.selectList(Wrappers.<AppUser>lambdaQuery()
                .and(!key.isBlank(), w -> w.like(AppUser::getUsernameLower, key)
                        .or().like(AppUser::getEmailLower, key))
                .orderByDesc(AppUser::getId)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
        return users.stream().map(this::userView).toList();
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String status = string(body.get("status"));
        if (status != null && !status.isBlank()) {
            if (!AppUser.STATUS_ACTIVE.equals(status) && !AppUser.STATUS_DISABLED.equals(status)) {
                throw new IllegalArgumentException("非法状态：" + status);
            }
            userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                    .eq(AppUser::getId, id)
                    .set(AppUser::getStatus, status)
                    .set(AppUser::getUpdatedAt, LocalDateTime.now()));
        }
        String role = string(body.get("role"));
        if (role != null && !role.isBlank()) {
            if (!AppUser.ROLE_USER.equals(role) && !AppUser.ROLE_ADMIN.equals(role)) {
                throw new IllegalArgumentException("非法角色：" + role);
            }
            userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                    .eq(AppUser::getId, id)
                    .set(AppUser::getRole, role)
                    .set(AppUser::getUpdatedAt, LocalDateTime.now()));
        }
        String memberCode = string(body.get("memberCode"));
        if (memberCode != null && !memberCode.isBlank()) {
            MemberPlan plan = membershipService.require(memberCode);
            int days = body.get("memberDays") == null
                    ? (plan.getDurationDays() == null ? 30 : plan.getDurationDays())
                    : Integer.parseInt(String.valueOf(body.get("memberDays")));
            membershipService.upgrade(id, memberCode, days);
        }
        auditService.record(UserContext.requireUserId(), "ADMIN_USER_UPDATE", "user", id,
                "role=" + role + ",status=" + status + ",member=" + memberCode);
        return userView(userMapper.selectById(id));
    }

    @GetMapping("/plans")
    public List<MemberPlan> plans() {
        return membershipService.listAll();
    }

    @PutMapping("/plans/{code}")
    public MemberPlan updatePlan(@PathVariable String code, @RequestBody MemberPlan request) {
        MemberPlan plan = membershipService.require(code);
        if (request.getName() != null) {
            plan.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            plan.setDescription(request.getDescription());
        }
        if (request.getPriceCents() != null) {
            plan.setPriceCents(request.getPriceCents());
        }
        if (request.getDurationDays() != null) {
            plan.setDurationDays(request.getDurationDays());
        }
        if (request.getDailyCredits() != null) {
            plan.setDailyCredits(request.getDailyCredits());
        }
        if (request.getMaxConcurrent() != null) {
            plan.setMaxConcurrent(request.getMaxConcurrent());
        }
        if (request.getMaxResolution() != null) {
            plan.setMaxResolution(request.getMaxResolution());
        }
        if (request.getDispatchWeight() != null) {
            plan.setDispatchWeight(request.getDispatchWeight());
        }
        if (request.getEnabled() != null) {
            plan.setEnabled(request.getEnabled());
        }
        if (request.getSortOrder() != null) {
            plan.setSortOrder(request.getSortOrder());
        }
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        auditService.record(UserContext.requireUserId(), "ADMIN_PLAN_UPDATE", "member_plan", plan.getCode(), null);
        return membershipService.require(code);
    }

    @GetMapping("/credit-packages")
    public List<CreditPackage> packages() {
        return packageMapper.selectList(Wrappers.<CreditPackage>lambdaQuery()
                .orderByAsc(CreditPackage::getSortOrder));
    }

    @PostMapping("/credit-packages")
    public CreditPackage createPackage(@RequestBody CreditPackage request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("缺少商品编码");
        }
        request.setId(null);
        request.setUpdatedAt(LocalDateTime.now());
        packageMapper.insert(request);
        auditService.record(UserContext.requireUserId(), "ADMIN_PACKAGE_CREATE", "credit_package",
                request.getCode(), null);
        return request;
    }

    @PutMapping("/credit-packages/{id}")
    public CreditPackage updatePackage(@PathVariable Long id, @RequestBody CreditPackage request) {
        CreditPackage item = packageMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("积分商品不存在");
        }
        request.setId(id);
        request.setUpdatedAt(LocalDateTime.now());
        packageMapper.updateById(request);
        auditService.record(UserContext.requireUserId(), "ADMIN_PACKAGE_UPDATE", "credit_package", id, null);
        return packageMapper.selectById(id);
    }

    @DeleteMapping("/credit-packages/{id}")
    public Map<String, Object> deletePackage(@PathVariable Long id) {
        packageMapper.deleteById(id);
        auditService.record(UserContext.requireUserId(), "ADMIN_PACKAGE_DELETE", "credit_package", id, null);
        return Map.of("ok", true);
    }

    @GetMapping("/orders")
    public List<PaymentOrder> orders(@RequestParam(defaultValue = "50") int limit) {
        return paymentService.listAllOrders(limit);
    }

    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestParam(defaultValue = "100") int limit) {
        return auditMapper.selectList(Wrappers.<AuditLog>lambdaQuery()
                .orderByDesc(AuditLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
    }

    @GetMapping("/outbox/dead")
    public List<EventOutbox> deadEvents(@RequestParam(defaultValue = "50") int limit) {
        return outboxMapper.selectList(Wrappers.<EventOutbox>lambdaQuery()
                .eq(EventOutbox::getStatus, EventOutbox.STATUS_DEAD)
                .orderByDesc(EventOutbox::getId)
                .last("LIMIT " + Math.max(1, Math.min(200, limit))));
    }

    @PostMapping("/outbox/{id}/retry")
    public Map<String, Object> retryEvent(@PathVariable Long id) {
        EventOutbox event = outboxMapper.selectById(id);
        if (event == null) {
            throw new IllegalArgumentException("事件不存在");
        }
        outboxMapper.update(null, Wrappers.<EventOutbox>lambdaUpdate()
                .eq(EventOutbox::getId, id)
                .set(EventOutbox::getStatus, EventOutbox.STATUS_PENDING)
                .set(EventOutbox::getAttempts, 0)
                .set(EventOutbox::getNextAttemptAt, LocalDateTime.now())
                .set(EventOutbox::getLastError, null)
                .set(EventOutbox::getUpdatedAt, LocalDateTime.now()));
        auditService.record(UserContext.requireUserId(), "ADMIN_OUTBOX_RETRY", "event_outbox", id, null);
        return Map.of("ok", true);
    }

    private Map<String, Object> userView(AppUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        view.put("status", user.getStatus());
        view.put("emailVerified", Boolean.TRUE.equals(user.getEmailVerified()));
        view.put("memberCode", user.getMemberCode());
        view.put("memberExpiresAt", user.getMemberExpiresAt() == null ? "" : user.getMemberExpiresAt().toString());
        view.put("lastLoginAt", user.getLastLoginAt() == null ? "" : user.getLastLoginAt().toString());
        view.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        return view;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
