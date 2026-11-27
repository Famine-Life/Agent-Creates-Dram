package com.aigc.video.api.mcp;

import com.aigc.video.api.dto.CanvasNodeRequest;
import com.aigc.video.domain.CanvasNode;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.task.CanvasNodeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/** MCP：文本 / 脚本节点与脚本生成 */
@Service
@RequiredArgsConstructor
public class McpNodeTools {

    private final CanvasNodeService canvasNodeService;
    private final ObjectMapper objectMapper;
    private final McpToolSupport support;

    @Tool(name = "create_canvas_node",
            description = "在画布创建文本或脚本节点。type=script 时可用 generate_script 生成分镜脚本。")
    public JsonNode createCanvasNode(
            @ToolParam(description = "节点类型：text 或 script") String type,
            @ToolParam(required = false, description = "节点标题") String title,
            @ToolParam(required = false, description = "生成提示词，如剧情梗概") String prompt,
            @ToolParam(required = false, description = "初始正文") String content,
            @ToolParam(required = false, description = "横坐标") Double x,
            @ToolParam(required = false, description = "纵坐标") Double y,
            @ToolParam(required = false, description = "宽度（像素）") Double width,
            @ToolParam(required = false, description = "高度（像素）") Double height,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        String nodeType = McpToolSupport.requireText(type, "请提供节点类型：text 或 script");
        if (!CanvasNode.TYPE_TEXT.equals(nodeType) && !CanvasNode.TYPE_SCRIPT.equals(nodeType)) {
            throw new BadRequestException("不支持的节点类型：" + nodeType);
        }
        return McpToolSupport.toJson(objectMapper, canvasNodeService.createNode(
                support.workspace(workspaceId),
                new CanvasNodeRequest(nodeType, title, prompt, content, x, y, width, height)));
    }

    @Tool(name = "update_canvas_node",
            description = "更新文本 / 脚本节点的标题、提示词、正文或坐标。")
    public JsonNode updateCanvasNode(
            @ToolParam(description = "节点 ID") Long nodeId,
            @ToolParam(required = false, description = "新标题") String title,
            @ToolParam(required = false, description = "新提示词") String prompt,
            @ToolParam(required = false, description = "新正文") String content,
            @ToolParam(required = false, description = "横坐标") Double x,
            @ToolParam(required = false, description = "纵坐标") Double y,
            @ToolParam(required = false, description = "宽度（像素）") Double width,
            @ToolParam(required = false, description = "高度（像素）") Double height,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(nodeId, "节点ID");
        return McpToolSupport.toJson(objectMapper, canvasNodeService.updateNode(
                support.workspace(workspaceId), id,
                new CanvasNodeRequest(null, title, prompt, content, x, y, width, height)));
    }

    @Tool(name = "delete_canvas_node",
            description = "删除文本 / 脚本节点。危险操作：必须先向用户确认节点 ID，用户明确同意后再调用。")
    public JsonNode deleteCanvasNode(
            @ToolParam(description = "节点 ID") Long nodeId,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(nodeId, "节点ID");
        Long workspace = support.workspace(workspaceId);
        canvasNodeService.deleteNode(workspace, id);
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "nodeId", id, "workspaceId", workspace));
    }

    @Tool(name = "generate_script",
            description = "让文本模型根据剧情描述生成分镜脚本，结果以 JSON 数组写入脚本节点 content，异步完成。")
    public JsonNode generateScript(
            @ToolParam(description = "脚本节点 ID（type=script）") Long nodeId,
            @ToolParam(required = false, description = "剧情描述，不传则用节点已有提示词") String prompt,
            @ToolParam(required = false, description = "文本模型，形如 ark/doubao-seed-evolving") String model,
            @ToolParam(required = false, description = "画布 ID，默认 1") Long workspaceId) {
        Long id = McpToolSupport.requireId(nodeId, "节点ID");
        return McpToolSupport.toJson(objectMapper,
                canvasNodeService.generateScript(support.workspace(workspaceId), id, prompt, model));
    }
}
