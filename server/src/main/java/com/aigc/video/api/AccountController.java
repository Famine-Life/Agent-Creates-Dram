package com.aigc.video.api;

import com.aigc.video.api.dto.AuthDtos;
import com.aigc.video.audit.AuditService;
import com.aigc.video.billing.AccountService;
import com.aigc.video.billing.AccountSummary;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.CreditPackage;
import com.aigc.video.domain.McpToken;
import com.aigc.video.domain.MemberPlan;
import com.aigc.video.mapper.CreditPackageMapper;
import com.aigc.video.member.MembershipService;
import com.aigc.video.user.McpTokenService;
import com.aigc.video.user.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 我的账户：摘要、流水、会员商品与个人 MCP Token。 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final MembershipService membershipService;
    private final McpTokenService mcpTokenService;
    private final CreditPackageMapper packageMapper;
    private final AuditService auditService;

    @GetMapping
    public AccountSummary summary() {
        return accountService.summary(UserContext.requireUserId());
    }

    @GetMapping("/ledgers")
    public List<CreditLedger> ledgers(@RequestParam(defaultValue = "50") int limit) {
        return accountService.ledgers(UserContext.requireUserId(), limit);
    }

    @GetMapping("/plans")
    public List<MemberPlan> plans() {
        return membershipService.listEnabled();
    }

    @GetMapping("/packages")
    public List<CreditPackage> packages() {
        return packageMapper.selectList(Wrappers.<CreditPackage>lambdaQuery()
                .eq(CreditPackage::getEnabled, true)
                .orderByAsc(CreditPackage::getSortOrder));
    }

    @GetMapping("/mcp-tokens")
    public List<Map<String, Object>> mcpTokens() {
        return mcpTokenService.list(UserContext.requireUserId()).stream()
                .map(this::tokenView)
                .toList();
    }

    @PostMapping("/mcp-tokens")
    public McpTokenService.CreatedToken createMcpToken(@RequestBody(required = false) AuthDtos.McpTokenRequest request) {
        Long userId = UserContext.requireUserId();
        McpTokenService.CreatedToken created = mcpTokenService.create(userId,
                request == null ? null : request.name(),
                request == null ? null : request.expiresInDays());
        auditService.record(userId, "MCP_TOKEN_CREATE", "mcp_token", created.id(), created.name());
        return created;
    }

    @DeleteMapping("/mcp-tokens/{id}")
    public Map<String, Object> revokeMcpToken(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        mcpTokenService.revoke(userId, id);
        auditService.record(userId, "MCP_TOKEN_REVOKE", "mcp_token", id, null);
        return Map.of("ok", true);
    }

    private Map<String, Object> tokenView(McpToken token) {
        return Map.of(
                "id", token.getId(),
                "name", token.getName(),
                "prefix", token.getTokenPrefix(),
                "expiresAt", token.getExpiresAt() == null ? "" : token.getExpiresAt().toString(),
                "revokedAt", token.getRevokedAt() == null ? "" : token.getRevokedAt().toString(),
                "createdAt", token.getCreatedAt() == null ? "" : token.getCreatedAt().toString());
    }
}
