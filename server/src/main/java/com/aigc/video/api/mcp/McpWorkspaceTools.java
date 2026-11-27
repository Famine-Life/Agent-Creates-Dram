package com.aigc.video.api.mcp;

import com.aigc.video.ai.AiService;
import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.api.dto.CanvasState;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.OrderRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.ai.ModelCatalog.Capability;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.task.TaskService;
import com.aigc.video.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCP：画布管理、模型目录、素材查询与提示词辅助 */
@Service
@RequiredArgsConstructor
public class McpWorkspaceTools {

    private final WorkspaceService workspaceService;
    private final TaskService taskService;
    private final ModelCatalog modelCatalog;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final McpToolSupport support;

    @Tool(name = "get_canvas_state",
            description = "读取画布全部镜头、图片、文本节点、连线与生成状态，AI 续写或确认结果前先调用。")
    public JsonNode getCanvasState(
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        return McpToolSupport.toJson(objectMapper, taskService.getCanvas(support.workspace(workspaceId)));
    }

    @Tool(name = "list_workspaces",
            description = "列出所有画布（工作空间）及其 ID、名称与快照版本。操作其他工具前可先用它确认 workspaceId。")
    public JsonNode listWorkspaces() {
        return McpToolSupport.toJson(objectMapper, workspaceService.list(null));
    }

    @Tool(name = "create_workspace",
            description = "新建一张空白画布，名称按序号自动生成，返回新画布 ID。")
    public JsonNode createWorkspace() {
        return McpToolSupport.toJson(objectMapper, workspaceService.createWorkspace(null));
    }

    @Tool(name = "rename_workspace",
            description = "重命名画布。")
    public JsonNode renameWorkspace(
            @ToolParam(description = "画布 ID") Long workspaceId,
            @ToolParam(description = "新名称") String name) {
        Long id = McpToolSupport.requireId(workspaceId, "画布ID");
        workspaceService.renameWorkspace(id, McpToolSupport.requireText(name, "画布名称不能为空"));
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "workspaceId", id));
    }

    @Tool(name = "copy_workspace",
            description = "复制画布及其全部内容，返回新画布信息。")
    public JsonNode copyWorkspace(@ToolParam(description = "要复制的画布 ID") Long workspaceId) {
        Long id = McpToolSupport.requireId(workspaceId, "画布ID");
        return McpToolSupport.toJson(objectMapper, workspaceService.viewOf(workspaceService.copyWorkspace(id).getId()));
    }

    @Tool(name = "delete_workspace",
            description = "删除画布及其全部镜头、图片与节点。危险操作：必须先向用户确认画布 ID，用户明确同意后再调用。系统至少保留一个画布。")
    public JsonNode deleteWorkspace(@ToolParam(description = "要删除的画布 ID") Long workspaceId) {
        Long id = McpToolSupport.requireId(workspaceId, "画布ID");
        workspaceService.deleteWorkspace(id);
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "workspaceId", id));
    }

    @Tool(name = "list_models",
            description = "查看当前可用的视频 / 图片 / 文本模型及默认项。model 参数需传返回的 value（provider/model 形式）。")
    public JsonNode listModels(
            @ToolParam(required = false, description = "可选，video / image / text；不传则返回全部") String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (type == null || type.isBlank()) {
            result.put("video", capability(Capability.VIDEO));
            result.put("image", capability(Capability.IMAGE));
            result.put("text", capability(Capability.TEXT));
        } else {
            Capability capability = switch (type.trim().toLowerCase()) {
                case "video" -> Capability.VIDEO;
                case "image" -> Capability.IMAGE;
                case "text" -> Capability.TEXT;
                default -> throw new BadRequestException("type 仅支持 video / image / text");
            };
            result.put(capability.key(), capability(capability));
        }
        return McpToolSupport.toJson(objectMapper, result);
    }

    @Tool(name = "list_assets",
            description = "列出画布素材：已生成视频、AI 生成图片、用户上传图片。")
    public JsonNode listAssets(
            @ToolParam(required = false, description = "可选，video / ai / upload；不传则返回全部") String type,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        CanvasState canvas = taskService.getCanvas(support.workspace(workspaceId));
        List<Map<String, Object>> videos = new ArrayList<>();
        List<Map<String, Object>> ai = new ArrayList<>();
        List<Map<String, Object>> uploads = new ArrayList<>();
        for (TaskNode task : canvas.tasks()) {
            if (task.videoUrl() != null && !task.videoUrl().isBlank()) {
                videos.add(asset("video", task.id(), task.title(), task.videoUrl()));
            }
        }
        for (ImageNode image : canvas.images()) {
            if (image.url() == null || image.url().isBlank()) {
                continue;
            }
            if (ImageNode.SOURCE_AI.equals(image.source())) {
                ai.add(asset("ai", image.id(), image.name(), image.url()));
            } else {
                uploads.add(asset("upload", image.id(), image.name(), image.url()));
            }
        }
        if (type == null || type.isBlank()) {
            List<Map<String, Object>> all = new ArrayList<>();
            all.addAll(videos);
            all.addAll(ai);
            all.addAll(uploads);
            return McpToolSupport.toJson(objectMapper, all);
        }
        return switch (type.trim().toLowerCase()) {
            case "video" -> McpToolSupport.toJson(objectMapper, videos);
            case "ai" -> McpToolSupport.toJson(objectMapper, ai);
            case "upload" -> McpToolSupport.toJson(objectMapper, uploads);
            default -> throw new BadRequestException("type 仅支持 video / ai / upload");
        };
    }

    @Tool(name = "list_prompt_records",
            description = "查看画布最近 200 条提示词修改历史，用于复用或追溯。")
    public JsonNode listPromptRecords(
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        return McpToolSupport.toJson(objectMapper,
                taskService.listPromptRecords(support.workspace(workspaceId)));
    }

    @Tool(name = "optimize_prompt",
            description = "让文本模型润色/扩写视频或图片提示词，返回优化后的文本，不改动画布。")
    public JsonNode optimizePrompt(
            @ToolParam(description = "待优化的提示词") String prompt,
            @ToolParam(required = false, description = "文本模型，形如 ark/doubao-seed-evolving；不传用默认") String model) {
        String text = McpToolSupport.requireText(prompt, "请提供待优化的提示词");
        return McpToolSupport.toJson(objectMapper, Map.of("prompt", aiService.optimizePrompt(text, model)));
    }

    @Tool(name = "save_canvas_snapshot",
            description = "保存画布快照（节点坐标、视口等前端状态）。snapshotJson 为 JSON 对象字符串；baseVersion 用于乐观锁，冲突时返回 409 错误。")
    public JsonNode saveCanvasSnapshot(
            @ToolParam(description = "画布 ID") Long workspaceId,
            @ToolParam(description = "快照 JSON 对象字符串，例如 {\"positions\":{\"shot-1\":{\"x\":80,\"y\":100}}}") String snapshotJson,
            @ToolParam(required = false, description = "当前快照版本号，不传则跳过乐观锁") Integer baseVersion) {
        Long id = McpToolSupport.requireId(workspaceId, "画布ID");
        JsonNode snapshot = parseJson(snapshotJson, "snapshotJson");
        CanvasState state = taskService.saveSnapshot(id, snapshot, baseVersion);
        return McpToolSupport.toJson(objectMapper,
                Map.of("workspaceId", id, "version", state.version() == null ? 0 : state.version()));
    }

    @Tool(name = "save_shot_order",
            description = "批量保存镜头分组与镜号（拖拽排序结果）。itemsJson 为数组字符串，每项含 taskId / groupId / shotIndex。")
    public JsonNode saveShotOrder(
            @ToolParam(description = "画布 ID") Long workspaceId,
            @ToolParam(description = "排序 JSON 数组字符串，例如 [{\"taskId\":1,\"groupId\":\"A组\",\"shotIndex\":1}]") String itemsJson) {
        Long id = McpToolSupport.requireId(workspaceId, "画布ID");
        JsonNode root = parseJson(itemsJson, "itemsJson");
        if (!root.isArray()) {
            throw new BadRequestException("itemsJson 必须是 JSON 数组");
        }
        List<OrderRequest.OrderItem> items = new ArrayList<>();
        for (JsonNode node : root) {
            items.add(objectMapper.convertValue(node, OrderRequest.OrderItem.class));
        }
        CanvasState state = taskService.reorder(id, new OrderRequest(items));
        return McpToolSupport.toJson(objectMapper,
                Map.of("workspaceId", id, "items", items.size(),
                        "version", state.version() == null ? 0 : state.version()));
    }

    private Map<String, Object> capability(Capability capability) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", capability.label());
        data.put("default", modelCatalog.defaultKey(capability));
        data.put("models", modelCatalog.list(capability));
        return data;
    }

    private Map<String, Object> asset(String type, Long id, String title, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("id", id);
        item.put("title", title == null ? "" : title);
        item.put("url", url);
        return item;
    }

    private JsonNode parseJson(String text, String label) {
        McpToolSupport.requireText(text, label + " 不能为空");
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BadRequestException(label + " 解析失败：" + e.getMessage());
        }
    }
}
