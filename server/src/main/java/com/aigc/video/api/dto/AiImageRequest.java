package com.aigc.video.api.dto;

/** 在画布上创建 AI 图片节点 */
public record AiImageRequest(String prompt, Double x, Double y) {
}
