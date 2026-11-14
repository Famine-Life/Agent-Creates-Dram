package com.aigc.video.api.dto;

import java.util.List;

/** 新建 / 推送单个镜头 */
public record ShotRequest(
        String prompt,
        String title,
        String groupId,
        Integer shotIndex,
        List<String> imageUrls,
        List<Long> imageIds,
        String model,
        String ratio,
        Integer duration,
        String resolution
) {
    public static ShotRequest of(String prompt) {
        return new ShotRequest(prompt, null, null, null, null, null, null, null, null, null);
    }
}
