package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_task")
public class GenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Long canvasId;

    private String groupId;

    private Integer shotIndex;

    private String title;

    private String prompt;

    /** 生成模型（与供应商对应：mock / seedance） */
    private String model;

    /** 画面比例，如 16:9 */
    private String ratio;

    /** 视频时长（秒） */
    private Integer duration;

    /** 清晰度（480P / 720P / 1080P / 4K，随模型能力变化） */
    private String resolution;

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
