package com.aigc.video.api.dto;

/** 修改提示词 / 生成参数，regenerate=true 时清空旧进度立即重新生成 */
public record PromptUpdateRequest(
        String prompt,
        Boolean regenerate,
        String model,
        String ratio,
        Integer duration,
        String resolution
) {
}
