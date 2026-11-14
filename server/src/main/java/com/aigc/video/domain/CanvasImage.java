package com.aigc.video.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 画布上的图片节点（参考图） */
@Data
@TableName("canvas_image")
public class CanvasImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Long canvasId;

    private String name;

    private String url;

    /** 存储层对象键，本地存储时为文件名 */
    private String objectKey;

    private Double x;

    private Double y;

    private Double width;

    private Double height;

    /** AI 图片节点：提示词。上传的图片为空 */
    private String prompt;

    /** AI 图片节点状态：IDLE / RUNNING / SUCCEEDED / FAILED */
    private String status;

    private String errorMsg;

    private String provider;

    private LocalDateTime createdAt;
}
