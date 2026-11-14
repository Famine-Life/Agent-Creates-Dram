package com.aigc.video.api.dto;

/** 创建 / 更新文本、脚本节点 */
public record CanvasNodeRequest(
        String type,
        String title,
        String prompt,
        String content,
        Double x,
        Double y,
        Double width,
        Double height
) {
}
