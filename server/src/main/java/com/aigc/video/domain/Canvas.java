package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("canvas")
public class Canvas {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Integer version;

    /** 前端画布快照（节点坐标、视口），原样存储的 JSON 字符串 */
    private String snapshot;

    private LocalDateTime updatedAt;
}
