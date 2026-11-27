package com.aigc.video.mapper;

import com.aigc.video.domain.EventInbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * EventInbox 表 Mapper，用于记录各消费者已处理的事件 ID，保证消费幂等。
 */
public interface EventInboxMapper extends BaseMapper<EventInbox> {
}
