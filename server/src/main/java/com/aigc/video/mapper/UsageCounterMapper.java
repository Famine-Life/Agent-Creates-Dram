package com.aigc.video.mapper;

import com.aigc.video.domain.UsageCounter;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * UsageCounter 表 Mapper，负责日/小时调用次数与积分消费的计数读写。
 */
public interface UsageCounterMapper extends BaseMapper<UsageCounter> {
}
