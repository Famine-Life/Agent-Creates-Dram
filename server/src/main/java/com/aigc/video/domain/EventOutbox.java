package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 事务 Outbox 事件。 */
@Data
@TableName("event_outbox")
public class EventOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_DEAD = "DEAD";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String aggregateType;

    private Long aggregateId;

    private Long userId;

    private String eventType;

    private String payload;

    private String status;

    private Integer attempts;

    private LocalDateTime nextAttemptAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
