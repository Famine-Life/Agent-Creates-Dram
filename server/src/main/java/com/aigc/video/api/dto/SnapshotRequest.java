package com.aigc.video.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 画布快照：节点坐标 + 视口；baseVersion 用于乐观锁校验（为空时跳过校验） */
public record SnapshotRequest(JsonNode snapshot, Integer baseVersion) {
}
