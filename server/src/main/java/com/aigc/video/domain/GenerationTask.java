package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表示持久化业务数据。
 */
@Data
@TableName("generation_task")
public class GenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long workspaceId;

    private Long canvasId;

    /** 本次生成尝试的计费操作 ID，用于冻结/实扣/退回幂等 */
    private String billingOpId;

    /** 本次生成消耗的积分快照 */
    private Integer creditCost;

    private String groupId;

    private Integer shotIndex;

    private String title;

    private String prompt;

    /** 真实模型 ID，如 doubao-seedance-2-0-260128 */
    private String model;

    /** 画面比例，如 16:9 */
    private String ratio;

    /** 视频时长（秒） */
    private Integer duration;

    /** 清晰度（480P / 720P / 1080P / 4K，随模型能力变化） */
    private String resolution;

    /** 平台标识，如 ark / openai / mock；存量数据可能是 seedance / seedance-fast */
    private String provider;

    private String providerTaskId;

    private String status;

    private Integer progress;

    private String videoUrl;

    private String coverUrl;

    private String errorMsg;

    private Integer pollCount;

    private String idemKey;

    private LocalDateTime nextPollAt;

    private LocalDateTime submittedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
