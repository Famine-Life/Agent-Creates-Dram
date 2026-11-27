package com.aigc.video.api.dto;

import java.time.LocalDateTime;

/** 项目列表项：附带画布数量与最近更新时间。 */
public record ProjectView(
        Long id,
        String name,
        long canvasCount,
        LocalDateTime updatedAt
) {
}
