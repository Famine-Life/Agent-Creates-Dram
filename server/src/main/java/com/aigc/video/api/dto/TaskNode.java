package com.aigc.video.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 画布上的镜头节点 */
public record TaskNode(
        Long id,
        Long workspaceId,
        String groupId,
        Integer shotIndex,
        String title,
        String prompt,
        /** 真实模型 ID */
        String model,
        /** 前端使用的 provider/model 复合标识 */
        String modelKey,
        String ratio,
        Integer duration,
        String resolution,
        String provider,
        String providerTaskId,
        String status,
        Integer progress,
        String videoUrl,
        String coverUrl,
        String errorMsg,
        LocalDateTime submittedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Long> imageIds,
        List<String> imageUrls
) {
}
