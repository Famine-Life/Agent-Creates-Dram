package com.aigc.video.api.dto;

import java.time.LocalDateTime;

/** 项目列表项：附带画布数量与最近更新时间。 */
public record ProjectView(
        /** 项目 ID */
        Long id,
        /** 项目名称 */
        String name,
        /** 项目下画布数量 */
        long canvasCount,
        /** 项目与其画布中最近的更新时间 */
        LocalDateTime updatedAt
) {
}
