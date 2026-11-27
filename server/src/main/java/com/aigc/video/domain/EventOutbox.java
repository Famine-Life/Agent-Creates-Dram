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

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一事件 ID */
    private String eventId;

    /** 聚合类型，如 generation_task */
    private String aggregateType;

    /** 聚合根 ID，如任务 ID */
    private Long aggregateId;

    /** 事件归属用户 */
    private Long userId;

    /** 事件类型 */
    private String eventType;

    /** 事件负载 JSON */
    private String payload;

    /** 投递状态：待投递、投递中、已完成或死信 */
    private String status;

    /** 已尝试投递次数 */
    private Integer attempts;

    /** 下次可投递时间，用于失败退避 */
    private LocalDateTime nextAttemptAt;

    /** 最近一次失败原因，超长时截断 */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
