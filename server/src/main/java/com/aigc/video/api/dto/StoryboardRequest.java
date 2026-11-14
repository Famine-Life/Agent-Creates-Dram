package com.aigc.video.api.dto;

import java.util.List;

/** 批量推送分镜 */
public record StoryboardRequest(List<ShotRequest> shots, String groupId) {
}
