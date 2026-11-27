package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 消费幂等表。 */
@Data
@TableName("event_inbox")
public class EventInbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String consumer;

    private LocalDateTime processedAt;
}
