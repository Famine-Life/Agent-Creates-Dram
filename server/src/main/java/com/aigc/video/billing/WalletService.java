package com.aigc.video.billing;

import com.aigc.video.common.PaymentRequiredException;
import com.aigc.video.domain.CreditLedger;
import com.aigc.video.domain.Wallet;
import com.aigc.video.mapper.CreditLedgerMapper;
import com.aigc.video.mapper.WalletMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 积分钱包：冻结、实扣、退回，全部通过原子条件更新与流水唯一键保证幂等。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletMapper walletMapper;
    private final CreditLedgerMapper ledgerMapper;

    /** 获取钱包，不存在时创建，并发创建冲突则回读已有记录。 */
    public Wallet ensureWallet(Long userId) {
        Wallet wallet = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                .eq(Wallet::getUserId, userId).last("LIMIT 1"));
        if (wallet != null) {
            return wallet;
        }
        Wallet created = new Wallet();
        created.setUserId(userId);
        created.setAvailableCredits(0);
        created.setFrozenCredits(0);
        created.setVersion(0);
        created.setUpdatedAt(LocalDateTime.now());
        try {
            walletMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery()
                    .eq(Wallet::getUserId, userId).last("LIMIT 1"));
        }
    }

    /** 获取用户钱包。 */
    public Wallet walletOf(Long userId) {
        return ensureWallet(userId);
    }

    /** 冻结积分；余额不足返回 false 并写入失败流水。 */
    @Transactional
    public boolean reserve(Long userId, String bizId, int amount) {
        ensureWallet(userId);
        if (ledgerMapper.selectCount(Wrappers.<CreditLedger>lambdaQuery()
                .eq(CreditLedger::getUserId, userId)
                .eq(CreditLedger::getType, CreditLedger.TYPE_RESERVE)
                .eq(CreditLedger::getBizId, bizId)) > 0) {
            return true;
        }
        int updated = walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .eq(Wallet::getUserId, userId)
                .ge(Wallet::getAvailableCredits, amount)
                .setSql("available_credits = available_credits - " + amount)
                .setSql("frozen_credits = frozen_credits + " + amount)
                .setSql("version = version + 1")
                .set(Wallet::getUpdatedAt, LocalDateTime.now()));
        Wallet wallet = walletOf(userId);
        if (updated == 0) {
            record(userId, CreditLedger.TYPE_INSUFFICIENT, bizId, 0,
                    wallet.getAvailableCredits(), wallet.getFrozenCredits(),
                    CreditLedger.STATUS_FAILED, "积分不足，未能预留");
            return false;
        }
        record(userId, CreditLedger.TYPE_RESERVE, bizId, amount,
                wallet.getAvailableCredits(), wallet.getFrozenCredits(),
                CreditLedger.STATUS_FROZEN, "生成任务预留积分");
        return true;
    }

    /** 生成成功：冻结转实扣；重复调用不会重复扣费。 */
    @Transactional
    public void capture(Long userId, String bizId) {
        CreditLedger reserve = reserveLedger(userId, bizId);
        if (reserve == null || !CreditLedger.STATUS_FROZEN.equals(reserve.getStatus())) {
            return;
        }
        int amount = reserve.getAmount() == null ? 0 : reserve.getAmount();
        walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .eq(Wallet::getUserId, userId)
                .ge(Wallet::getFrozenCredits, amount)
                .setSql("frozen_credits = frozen_credits - " + amount)
                .setSql("version = version + 1")
                .set(Wallet::getUpdatedAt, LocalDateTime.now()));
        Wallet wallet = walletOf(userId);
        record(userId, CreditLedger.TYPE_CAPTURE, bizId, amount,
                wallet.getAvailableCredits(), wallet.getFrozenCredits(),
                CreditLedger.STATUS_SETTLED, "生成成功，扣除预留积分");
        settleReserve(userId, bizId);
    }

    /** 生成失败/取消：退回冻结积分；已实扣的不会退回。 */
    @Transactional
    public void release(Long userId, String bizId, String note) {
        CreditLedger reserve = reserveLedger(userId, bizId);
        if (reserve == null || !CreditLedger.STATUS_FROZEN.equals(reserve.getStatus())) {
            return;
        }
        int amount = reserve.getAmount() == null ? 0 : reserve.getAmount();
        walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .eq(Wallet::getUserId, userId)
                .ge(Wallet::getFrozenCredits, amount)
                .setSql("frozen_credits = frozen_credits - " + amount)
                .setSql("available_credits = available_credits + " + amount)
                .setSql("version = version + 1")
                .set(Wallet::getUpdatedAt, LocalDateTime.now()));
        Wallet wallet = walletOf(userId);
        record(userId, CreditLedger.TYPE_RELEASE, bizId, amount,
                wallet.getAvailableCredits(), wallet.getFrozenCredits(),
                CreditLedger.STATUS_SETTLED, note);
        settleReserve(userId, bizId);
    }

    /** 充值/会员赠送/管理员调整：直接增加可用积分，按 bizId 幂等。 */
    @Transactional
    public void credit(Long userId, String type, String bizId, int amount, String note) {
        ensureWallet(userId);
        if (ledgerMapper.selectCount(Wrappers.<CreditLedger>lambdaQuery()
                .eq(CreditLedger::getUserId, userId)
                .eq(CreditLedger::getType, type)
                .eq(CreditLedger::getBizId, bizId)) > 0) {
            return;
        }
        walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .eq(Wallet::getUserId, userId)
                .setSql("available_credits = available_credits + " + amount)
                .setSql("version = version + 1")
                .set(Wallet::getUpdatedAt, LocalDateTime.now()));
        Wallet wallet = walletOf(userId);
        record(userId, type, bizId, amount,
                wallet.getAvailableCredits(), wallet.getFrozenCredits(),
                CreditLedger.STATUS_SETTLED, note);
    }

    /** 校验可用余额是否足够，不足时抛出支付异常。 */
    public void requireBalance(Long userId, int amount) {
        Wallet wallet = walletOf(userId);
        if (wallet.getAvailableCredits() == null || wallet.getAvailableCredits() < amount) {
            throw new PaymentRequiredException("INSUFFICIENT_BALANCE", "积分不足，请先充值");
        }
    }

    private CreditLedger reserveLedger(Long userId, String bizId) {
        return ledgerMapper.selectOne(Wrappers.<CreditLedger>lambdaQuery()
                .eq(CreditLedger::getUserId, userId)
                .eq(CreditLedger::getType, CreditLedger.TYPE_RESERVE)
                .eq(CreditLedger::getBizId, bizId).last("LIMIT 1"));
    }

    private void settleReserve(Long userId, String bizId) {
        ledgerMapper.update(null, Wrappers.<CreditLedger>lambdaUpdate()
                .eq(CreditLedger::getUserId, userId)
                .eq(CreditLedger::getType, CreditLedger.TYPE_RESERVE)
                .eq(CreditLedger::getBizId, bizId)
                .set(CreditLedger::getStatus, CreditLedger.STATUS_SETTLED));
    }

    private void record(Long userId, String type, String bizId, int amount,
                        Integer available, Integer frozen, String status, String note) {
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setType(type);
        ledger.setBizId(bizId);
        ledger.setAmount(amount);
        ledger.setAvailableAfter(available);
        ledger.setFrozenAfter(frozen);
        ledger.setStatus(status);
        ledger.setNote(note);
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            ledgerMapper.insert(ledger);
        } catch (DuplicateKeyException e) {
            log.debug("积分流水已存在，忽略重复写入 userId={} type={} bizId={}", userId, type, bizId);
        }
    }
}
