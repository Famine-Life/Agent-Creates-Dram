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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件 ID，与消费者共同构成幂等键 */
    private String eventId;

    /** 消费者名称 */
    private String consumer;

    /** 处理完成时间 */
    private LocalDateTime processedAt;
}
