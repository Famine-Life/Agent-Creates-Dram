package com.aigc.video.api.dto;

/** 创建 / 更新图片节点 */
public record ImageRequest(
        String name,
        String url,
        String objectKey,
        Double x,
        Double y,
        Double width,
        Double height,
        String prompt,
        String status,
        String source,
        Long sourceImageId
) {
}
