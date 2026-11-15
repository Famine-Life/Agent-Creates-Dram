package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 画布上的文本 / 脚本节点 */
/**
 * 表示持久化业务数据。
 */
@Data
@TableName("canvas_node")
public class CanvasNode {

    /** 文本节点 */
    public static final String TYPE_TEXT = "text";
    /** 脚本生成器节点 */
    public static final String TYPE_SCRIPT = "script";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private String nodeType;

    private String title;

    private String prompt;

    /** 文本内容 / 生成出的分镜脚本（JSON 数组） */
    private String content;

    /** IDLE / RUNNING / SUCCEEDED / FAILED */
    private String status;

    private String errorMsg;

    private Double x;

    private Double y;

    private Double width;

    private Double height;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
