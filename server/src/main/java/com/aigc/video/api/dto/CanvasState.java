package com.aigc.video.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 画布全量状态，刷新页面 / WS 重连后用它恢复 */
public record CanvasState(
        Long workspaceId,
        Long canvasId,
        Integer version,
        JsonNode snapshot,
        List<TaskNode> tasks,
        List<ImageNode> images,
        List<CanvasNodeView> nodes,
        List<EdgeView> edges
) {
}
