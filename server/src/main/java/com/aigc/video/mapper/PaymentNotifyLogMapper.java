package com.aigc.video.mapper;

import com.aigc.video.domain.PaymentNotifyLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * PaymentNotifyLog 表 Mapper，记录各支付渠道异步通知的验签与处理结果。
 */
public interface PaymentNotifyLogMapper extends BaseMapper<PaymentNotifyLog> {
}
