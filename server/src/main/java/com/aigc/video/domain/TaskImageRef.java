package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片 -> 镜头 的引用。
 * 一条记录同时代表「画布上一条连线」与「提示词里的 @图片N」。
 */
@Data
@TableName("task_image_ref")
public class TaskImageRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long imageId;

    /** 引用顺序，从 0 开始，对应 @图片1 */
    private Integer sortIndex;

    private LocalDateTime createdAt;
}
