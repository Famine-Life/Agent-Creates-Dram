package com.aigc.video.mapper;

import com.aigc.video.domain.EventOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * EventOutbox 表 Mapper，负责事务 Outbox 事件的持久化与投递状态流转。
 */
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
