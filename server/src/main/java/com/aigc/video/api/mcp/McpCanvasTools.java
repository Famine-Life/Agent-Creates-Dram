package com.aigc.video.api.mcp;

import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.StoryboardRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.task.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 暴露给 MCP 客户端（WorkBuddy / Trae 等）的工具。
 * 外部 AI 聊出分镜后调用这里，画布上就会长出一个视频节点。
 */
@Service
@RequiredArgsConstructor
public class McpCanvasTools {

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Tool(name = "push_shot",
            description = "把单个分镜/镜头提示词推送到视频画布，立即在画布上创建一个视频生成节点。"
                    + "可传参考图公网 URL 或已上传到系统的图片 ID，提示词里用 @图片1 引用第一张图。")
    public JsonNode pushShot(
            @ToolParam(description = "视频提示词，必填，最多 2000 字") String prompt,
            @ToolParam(required = false, description = "镜头标题，不传则自动生成") String title,
            @ToolParam(required = false, description = "分组名，同组镜头在画布上横排显示") String groupId,
            @ToolParam(required = false, description = "组内镜号，从 1 开始，不传则自动递增") Integer shotIndex,
            @ToolParam(required = false, description = "参考图公网 URL 列表，按顺序对应 @图片1、@图片2") List<String> imageUrls,
            @ToolParam(required = false, description = "已上传到系统的图片 ID 列表") List<Long> imageIds,
            @ToolParam(required = false, description = "工作空间 ID，默认 1") Long workspaceId) {
        ShotRequest request = new ShotRequest(prompt, title, groupId, shotIndex, imageUrls, imageIds, null, null, null, null, null);
        TaskNode node = taskService.createShot(defaultWorkspace(workspaceId), request);
        return toJson(node);
    }

    @Tool(name = "push_storyboard",
            description = "批量推送一组分镜，入参为 JSON 数组字符串，每项可含 prompt/title/groupId/shotIndex/imageUrls/imageIds 字段。")
    public JsonNode pushStoryboard(
            @ToolParam(description = "分镜 JSON 数组字符串，例如 [{\"prompt\":\"海边日落空镜\"}]") String shotsJson,
            @ToolParam(required = false, description = "默认分组名，未在分镜中指定的使用该值") String groupId,
            @ToolParam(required = false, description = "工作空间 ID，默认 1") Long workspaceId) {
        List<ShotRequest> shots = parseShots(shotsJson);
        List<TaskNode> nodes = taskService.createStoryboard(defaultWorkspace(workspaceId),
                new StoryboardRequest(shots, groupId));
        return toJson(nodes);
    }

    @Tool(name = "get_canvas_state",
            description = "查询画布当前状态：所有镜头节点、参考图节点与连线，用于续写或检查生成进度。")
    public JsonNode getCanvasState(
            @ToolParam(required = false, description = "工作空间 ID，默认 1") Long workspaceId) {
        return toJson(taskService.getCanvas(defaultWorkspace(workspaceId)));
    }

    private List<ShotRequest> parseShots(String shotsJson) {
        if (shotsJson == null || shotsJson.isBlank()) {
            throw new BadRequestException("shotsJson 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(shotsJson);
            if (!root.isArray()) {
                throw new BadRequestException("shotsJson 必须是 JSON 数组");
            }
            List<ShotRequest> shots = new ArrayList<>();
            for (JsonNode node : root) {
                shots.add(objectMapper.treeToValue(node, ShotRequest.class));
            }
            return shots;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("shotsJson 解析失败：" + e.getMessage());
        }
    }

    private Long defaultWorkspace(Long workspaceId) {
        return workspaceId == null ? 1L : workspaceId;
    }

    /** 返回 JsonNode，避免 MCP 层把字符串再包一层引号（双重转义） */
    private JsonNode toJson(Object value) {
        return objectMapper.valueToTree(value);
    }
}
