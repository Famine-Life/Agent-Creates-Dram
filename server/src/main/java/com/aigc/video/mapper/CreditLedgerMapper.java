package com.aigc.video.mapper;

import com.aigc.video.domain.CreditLedger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * CreditLedger 表 Mapper，负责冻结、实扣、退回等积分流水的写入与查询。
 */
public interface CreditLedgerMapper extends BaseMapper<CreditLedger> {
}
