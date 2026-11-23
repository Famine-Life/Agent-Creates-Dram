package com.aigc.video.api.mcp;

import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.api.dto.CanvasState;
import com.aigc.video.api.dto.EdgeRequest;
import com.aigc.video.api.dto.EdgeView;
import com.aigc.video.api.dto.ImageGenerateRequest;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.ImageRequest;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.storage.AssetService;
import com.aigc.video.storage.StoredAsset;
import com.aigc.video.task.CanvasNodeService;
import com.aigc.video.task.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCP：参考图上传、图片节点、AI 图片生成与图片连线 */
@Service
@RequiredArgsConstructor
public class McpImageTools {

    private final TaskService taskService;
    private final CanvasNodeService canvasNodeService;
    private final AssetService assetService;
    private final ModelCatalog modelCatalog;
    private final ObjectMapper objectMapper;

    @Tool(name = "upload_image",
            description = "上传参考图并在画布创建图片节点。传 url 时由服务端下载转存；传 base64Data 时直接保存内容（二选一）。")
    public JsonNode uploadImage(
            @ToolParam(required = false, description = "图片公网 URL，与 base64Data 二选一") String url,
            @ToolParam(required = false, description = "图片 base64 内容（可带 data:image/png;base64, 前缀），与 url 二选一") String base64Data,
            @ToolParam(required = false, description = "文件名，用于推断格式与节点名称，如 ref.png") String fileName,
            @ToolParam(required = false, description = "节点横坐标，不传由前端自动排布") Double x,
            @ToolParam(required = false, description = "节点纵坐标") Double y,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        boolean hasUrl = url != null && !url.isBlank();
        boolean hasBase64 = base64Data != null && !base64Data.isBlank();
        if (hasUrl == hasBase64) {
            throw new BadRequestException("url 与 base64Data 必须且只能提供一个");
        }
        String name = fileName == null || fileName.isBlank() ? "参考图" : fileName.trim();
        String storedUrl;
        String objectKey = null;
        if (hasUrl) {
            storedUrl = assetService.transfer(url.trim(), "images", ".jpg");
        } else {
            String contentType = mimeOf(base64Data, name);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(stripDataUri(base64Data));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("base64Data 不是合法的 Base64 内容");
            }
            StoredAsset stored = assetService.storeImage(name, contentType, bytes);
            storedUrl = stored.url();
            objectKey = stored.objectKey();
        }
        ImageNode node = taskService.createImage(McpToolSupport.workspace(workspaceId),
                new ImageRequest(name, storedUrl, objectKey, x, y, null, null, null, null,
                        ImageNode.SOURCE_UPLOAD, null));
        return McpToolSupport.toJson(objectMapper, node);
    }

    @Tool(name = "create_image_node",
            description = "在画布创建图片节点（只保存图片地址，不下载）。可用于引用外部参考图。")
    public JsonNode createImageNode(
            @ToolParam(description = "图片 URL") String url,
            @ToolParam(required = false, description = "节点名称") String name,
            @ToolParam(required = false, description = "图片提示词或说明") String prompt,
            @ToolParam(required = false, description = "节点横坐标") Double x,
            @ToolParam(required = false, description = "节点纵坐标") Double y,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        ImageNode node = taskService.createImage(McpToolSupport.workspace(workspaceId),
                new ImageRequest(name, McpToolSupport.requireText(url, "图片地址不能为空"), null,
                        x, y, null, null, prompt, null, ImageNode.SOURCE_UPLOAD, null));
        return McpToolSupport.toJson(objectMapper, node);
    }

    @Tool(name = "create_ai_image_node",
            description = "在画布创建 AI 图片节点（先占位）。之后用 generate_image 提交生成。")
    public JsonNode createAiImageNode(
            @ToolParam(required = false, description = "图片提示词") String prompt,
            @ToolParam(required = false, description = "节点横坐标") Double x,
            @ToolParam(required = false, description = "节点纵坐标") Double y,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        return McpToolSupport.toJson(objectMapper,
                canvasNodeService.createAiImage(McpToolSupport.workspace(workspaceId), x, y, prompt));
    }

    @Tool(name = "generate_image",
            description = "提交 AI 图片生成，立即返回排队状态，完成后画布自动更新。referenceUrl 非空时做图生图。")
    public JsonNode generateImage(
            @ToolParam(description = "图片节点 ID") Long imageId,
            @ToolParam(required = false, description = "提示词，不传则用节点已有提示词") String prompt,
            @ToolParam(required = false, description = "图片模型，形如 ark/doubao-seedream-4-0-20260415") String model,
            @ToolParam(required = false, description = "参考图 URL，用于图生图") String referenceUrl,
            @ToolParam(required = false, description = "画面比例，如 1:1、16:9") String ratio,
            @ToolParam(required = false, description = "清晰度：1K / 1.5K / 2K") String resolution,
            @ToolParam(required = false, description = "生成数量 1-9，默认 1") Integer count,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(imageId, "图片ID");
        List<String> references = referenceUrl == null || referenceUrl.isBlank()
                ? List.of() : List.of(referenceUrl);
        return McpToolSupport.toJson(objectMapper, canvasNodeService.generateImage(
                McpToolSupport.workspace(workspaceId), id, prompt, model, references, ratio, resolution, count));
    }

    @Tool(name = "update_image_node",
            description = "更新图片节点的名称、地址、提示词或坐标。")
    public JsonNode updateImageNode(
            @ToolParam(description = "图片节点 ID") Long imageId,
            @ToolParam(required = false, description = "新名称") String name,
            @ToolParam(required = false, description = "新图片 URL") String url,
            @ToolParam(required = false, description = "新提示词") String prompt,
            @ToolParam(required = false, description = "横坐标") Double x,
            @ToolParam(required = false, description = "纵坐标") Double y,
            @ToolParam(required = false, description = "宽度（像素）") Double width,
            @ToolParam(required = false, description = "高度（像素）") Double height,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(imageId, "图片ID");
        return McpToolSupport.toJson(objectMapper, taskService.updateImage(
                McpToolSupport.workspace(workspaceId), id,
                new ImageRequest(name, url, null, x, y, width, height, prompt, null, null, null)));
    }

    @Tool(name = "set_image_model",
            description = "只保存图片节点使用的图片模型，不提交生成。")
    public JsonNode setImageModel(
            @ToolParam(description = "图片节点 ID") Long imageId,
            @ToolParam(description = "图片模型，形如 ark/doubao-seedream-4-0-20260415") String model,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(imageId, "图片ID");
        String target = McpToolSupport.requireText(model, "请提供图片模型");
        Long workspace = McpToolSupport.workspace(workspaceId);

        CanvasState data = taskService.getCanvas(workspace);
        ImageNode image = data.images().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("画布 #" + workspace + " 中找不到图片 #" + id));
        ModelCatalog.ModelOption option = modelCatalog.list(ModelCatalog.Capability.IMAGE).stream()
                .filter(item -> item.value().equals(target))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("当前图片模型目录中找不到：" + target));

        ObjectNode snapshot = data.snapshot() != null && data.snapshot().isObject()
                ? ((ObjectNode) data.snapshot()).deepCopy() : objectMapper.createObjectNode();
        JsonNode rawSettings = snapshot.get("imageSettings");
        ObjectNode settings = rawSettings != null && rawSettings.isObject()
                ? ((ObjectNode) rawSettings).deepCopy() : objectMapper.createObjectNode();
        JsonNode rawEntry = settings.get(String.valueOf(id));
        ObjectNode entry = rawEntry != null && rawEntry.isObject()
                ? ((ObjectNode) rawEntry).deepCopy() : objectMapper.createObjectNode();
        entry.put("model", option.value());
        settings.set(String.valueOf(id), entry);
        snapshot.set("imageSettings", settings);

        CanvasState updated = taskService.saveSnapshot(workspace, snapshot, data.version());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", workspace);
        result.put("imageId", id);
        result.put("name", image.name());
        result.put("model", option.value());
        result.put("modelLabel", option.label());
        result.put("generated", false);
        result.put("version", updated.version() == null ? 0 : updated.version());
        return McpToolSupport.toJson(objectMapper, result);
    }

    @Tool(name = "link_image",
            description = "把图片连接到镜头，并按参考图顺序写入「@图片N：说明」。只保存连线与提示词，不提交生成。")
    public JsonNode linkImage(
            @ToolParam(description = "镜头任务 ID") Long taskId,
            @ToolParam(description = "图片节点 ID") Long imageId,
            @ToolParam(required = false, description = "图片里的角色、场景或道具，以及在本镜头中的参考用途") String description,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long shotId = McpToolSupport.requireId(taskId, "镜头ID");
        Long imgId = McpToolSupport.requireId(imageId, "图片ID");
        EdgeView edge = taskService.createEdgeWithPrompt(McpToolSupport.workspace(workspaceId),
                new EdgeRequest(shotId, imgId, description));
        return McpToolSupport.toJson(objectMapper, edge);
    }

    @Tool(name = "unlink_image",
            description = "删除图片与镜头之间的连线。")
    public JsonNode unlinkImage(
            @ToolParam(description = "连线 ID") Long edgeId,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(edgeId, "连线ID");
        taskService.deleteEdge(McpToolSupport.workspace(workspaceId), id);
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "edgeId", id));
    }

    @Tool(name = "delete_image_node",
            description = "删除图片节点及其与镜头的连线。危险操作：必须先向用户确认图片 ID，用户明确同意后再调用。")
    public JsonNode deleteImageNode(
            @ToolParam(description = "图片节点 ID") Long imageId,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(imageId, "图片ID");
        Long workspace = McpToolSupport.workspace(workspaceId);
        taskService.deleteImage(workspace, id);
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "imageId", id, "workspaceId", workspace));
    }

    private String stripDataUri(String value) {
        String text = value.trim();
        int comma = text.indexOf(',');
        if (text.startsWith("data:") && comma > 0) {
            return text.substring(comma + 1);
        }
        return text;
    }

    private String mimeOf(String base64Data, String fileName) {
        String text = base64Data.trim();
        if (text.startsWith("data:")) {
            int end = text.indexOf(';');
            if (end > 5) {
                return text.substring(5, end);
            }
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
