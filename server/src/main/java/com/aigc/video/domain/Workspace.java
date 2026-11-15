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
@TableName("workspace")
public class Workspace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime createdAt;
}
