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
        String model,
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
