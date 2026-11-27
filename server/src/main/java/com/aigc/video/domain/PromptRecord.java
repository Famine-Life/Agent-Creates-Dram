package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 每次提交或修改镜头提示词的历史记录 */
/**
 * 表示持久化业务数据。
 */
@Data
@TableName("prompt_record")
public class PromptRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long workspaceId;

    private Long taskId;

    private String prompt;

    private LocalDateTime createdAt;
}
