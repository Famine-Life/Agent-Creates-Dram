package com.aigc.video.api.dto;

/** 画布上的图片节点 */
public record ImageNode(
        Long id,
        String name,
        String url,
        Double x,
        Double y,
        Double width,
        Double height,
        String prompt,
        String status,
        String errorMsg
) {
}
