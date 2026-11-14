package com.aigc.video.api.dto;

/** 画布上的文本 / 脚本节点 */
public record CanvasNodeView(
        Long id,
        Long workspaceId,
        String type,
        String title,
        String prompt,
        String content,
        String status,
        String errorMsg,
        Double x,
        Double y,
        Double width,
        Double height
) {
}
