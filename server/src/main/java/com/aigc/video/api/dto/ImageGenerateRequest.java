package com.aigc.video.api.dto;

import java.util.List;

/**
 * 触发 AI 图片生成；referenceUrls 非空时按顺序作为图生图参考。
 * referenceUrl 为兼容旧调用方保留的单参考图字段。
 */
public record ImageGenerateRequest(
        String prompt,
        String model,
        /** 单参考图，兼容旧调用；与 referenceUrls 同时存在时以 referenceUrls 为准 */
        String referenceUrl,
        /** 多参考图，按提示词中的引用顺序排列 */
        List<String> referenceUrls,
        /** 画面比例，如 16:9、1:1 */
        String ratio,
        /** 清晰度：1K / 1.5K / 2K */
        String resolution,
        /** 生成数量，1-9 */
        Integer count
) {
}
