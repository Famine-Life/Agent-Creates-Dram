package com.aigc.video.api.dto;

/** 触发 AI 图片生成；referenceUrl 非空时以该图作为参考做图生图 */
public record ImageGenerateRequest(
        String prompt,
        String model,
        String referenceUrl,
        /** 画面比例，如 16:9、1:1 */
        String ratio,
        /** 清晰度：1K / 1.5K / 2K */
        String resolution,
        /** 生成数量，1-9 */
        Integer count
) {
}
