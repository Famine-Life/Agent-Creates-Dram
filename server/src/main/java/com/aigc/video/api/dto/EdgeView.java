package com.aigc.video.api.dto;

/** 图片 -> 镜头 连线 */
public record EdgeView(Long id, Long taskId, Long imageId, Integer sortIndex) {
}
