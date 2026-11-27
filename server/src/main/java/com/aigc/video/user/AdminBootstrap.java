package com.aigc.video.user;

import com.aigc.video.billing.WalletService;
import com.aigc.video.domain.AppUser;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.CanvasNode;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.CreditPackage;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.domain.TaskImageRef;
import com.aigc.video.domain.Wallet;
import com.aigc.video.mapper.AppUserMapper;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.CanvasNodeMapper;
import com.aigc.video.mapper.CreditPackageMapper;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.mapper.PromptRecordMapper;
import com.aigc.video.mapper.TaskImageRefMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.member.MembershipService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 首次启动初始化管理员、播种会员与积分商品，并把单用户旧数据归属管理员。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final CanvasMapper canvasMapper;
    private final GenerationTaskMapper taskMapper;
    private final PromptRecordMapper promptRecordMapper;
    private final CanvasImageMapper imageMapper;
    private final CanvasNodeMapper nodeMapper;
    private final TaskImageRefMapper refMapper;
    private final CreditPackageMapper packageMapper;
    private final PasswordEncoder passwordEncoder;
    private final MembershipService membershipService;
    private final WalletService walletService;
    private final com.aigc.video.workspace.ProjectService projectService;

    @Value("${aigc.security.admin-username:admin}")
    private String adminUsername;

    @Value("${aigc.security.admin-password:admin@888}")
    private String adminPassword;

    @Value("${aigc.security.admin-email:admin@local}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        membershipService.seedDefaults();
        seedPackages();
        AppUser admin = ensureAdmin();
        int migrated = backfill(admin.getId());
        int grouped = projectService.backfillDefaultProjects();
        log.info("多用户会员制初始化完成：管理员={} 旧数据迁移记录={} 归入默认项目的画布={}",
                admin.getUsername(), migrated, grouped);
    }

    private AppUser ensureAdmin() {
        String username = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();
        String lower = username.toLowerCase();
        AppUser existing = userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getUsernameLower, lower).last("LIMIT 1"));
        if (existing != null) {
            if (!AppUser.ROLE_ADMIN.equals(existing.getRole())) {
                userMapper.update(null, Wrappers.<AppUser>lambdaUpdate()
                        .eq(AppUser::getId, existing.getId())
                        .set(AppUser::getRole, AppUser.ROLE_ADMIN));
                existing.setRole(AppUser.ROLE_ADMIN);
            }
            walletService.ensureWallet(existing.getId());
            return existing;
        }
        String email = adminEmail == null || adminEmail.isBlank() ? "admin@local" : adminEmail.trim();
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setUsernameLower(lower);
        admin.setEmail(email);
        admin.setEmailLower(email.toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword == null ? "admin@888" : adminPassword));
        admin.setRole(AppUser.ROLE_ADMIN);
        admin.setStatus(AppUser.STATUS_ACTIVE);
        admin.setEmailVerified(true);
        admin.setMemberCode(MemberPlan.STUDIO);
        admin.setMemberExpiresAt(LocalDateTime.now().plusYears(10));
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(admin);
        walletService.ensureWallet(admin.getId());
        Wallet wallet = walletService.walletOf(admin.getId());
        if ((wallet.getAvailableCredits() == null || wallet.getAvailableCredits() == 0)
                && (wallet.getFrozenCredits() == null || wallet.getFrozenCredits() == 0)) {
            walletService.credit(admin.getId(), CreditLedger.TYPE_ADJUST, "bootstrap-admin", 1000,
                    "管理员初始积分");
        }
        return admin;
    }

    private int backfill(Long adminId) {
        int count = 0;
        count += workspaceMapper.update(null, Wrappers.<com.aigc.video.domain.Workspace>lambdaUpdate()
                .isNull(com.aigc.video.domain.Workspace::getUserId)
                .set(com.aigc.video.domain.Workspace::getUserId, adminId));
        count += canvasMapper.update(null, Wrappers.<Canvas>lambdaUpdate()
                .isNull(Canvas::getUserId).set(Canvas::getUserId, adminId));
        count += taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .isNull(GenerationTask::getUserId).set(GenerationTask::getUserId, adminId));
        count += promptRecordMapper.update(null, Wrappers.<PromptRecord>lambdaUpdate()
                .isNull(PromptRecord::getUserId).set(PromptRecord::getUserId, adminId));
        count += imageMapper.update(null, Wrappers.<CanvasImage>lambdaUpdate()
                .isNull(CanvasImage::getUserId).set(CanvasImage::getUserId, adminId));
        count += nodeMapper.update(null, Wrappers.<CanvasNode>lambdaUpdate()
                .isNull(CanvasNode::getUserId).set(CanvasNode::getUserId, adminId));
        count += refMapper.update(null, Wrappers.<TaskImageRef>lambdaUpdate()
                .isNull(TaskImageRef::getUserId).set(TaskImageRef::getUserId, adminId));
        return count;
    }

    private void seedPackages() {
        seedPackage("CREDITS_100", "100 积分", 100, 990, 0);
        seedPackage("CREDITS_500", "500 积分", 500, 3990, 1);
        seedPackage("CREDITS_2000", "2000 积分", 2000, 12900, 2);
    }

    private void seedPackage(String code, String name, int credits, int priceCents, int sort) {
        Long count = packageMapper.selectCount(Wrappers.<CreditPackage>lambdaQuery()
                .eq(CreditPackage::getCode, code));
        if (count != null && count > 0) {
            return;
        }
        CreditPackage item = new CreditPackage();
        item.setCode(code);
        item.setName(name);
        item.setCredits(credits);
        item.setPriceCents(priceCents);
        item.setEnabled(true);
        item.setSortOrder(sort);
        item.setUpdatedAt(LocalDateTime.now());
        packageMapper.insert(item);
    }
}
