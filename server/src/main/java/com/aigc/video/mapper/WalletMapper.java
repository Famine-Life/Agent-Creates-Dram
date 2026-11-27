package com.aigc.video.mapper;

import com.aigc.video.domain.Wallet;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * Wallet 表 Mapper，负责可用与冻结积分的原子条件更新。
 */
public interface WalletMapper extends BaseMapper<Wallet> {
}
