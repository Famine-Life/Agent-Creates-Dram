package com.aigc.video.api.dto;

import java.time.LocalDateTime;

/** 画布切换列表项：一个 workspace 对应一张 canvas */
public record WorkspaceView(
        /** 工作空间 ID，同时作为切换画布使用的 ID */
        Long id,
        /** 画布显示名称，如“默认工作空间”“画布 2” */
        String name,
        /** 对应的画布快照 ID，暂无快照时为空 */
        Long canvasId,
        /** 快照版本号，用于保存时的乐观锁校验 */
        Integer version,
        /** 快照最后更新时间 */
        LocalDateTime updatedAt,
        /** 所属项目 ID */
        Long projectId
) {
}
