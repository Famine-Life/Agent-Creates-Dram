package com.aigc.video.api.mcp;

import com.aigc.video.api.dto.CanvasNodeView;
import com.aigc.video.api.dto.CanvasState;
import com.aigc.video.api.dto.EdgeView;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.PromptUpdateRequest;
import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.StoryboardRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.ConflictException;
import com.aigc.video.task.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** MCP：镜头（视频任务）的创建、生成控制与模型切换 */
@Service
@RequiredArgsConstructor
public class McpShotTools {

    // 与前端 App.vue、CLI 的布局常量保持一致：镜头卡 320×220，下方为编辑器与参考图预留 230
    private static final double SHOT_W = 320;
    private static final double SHOT_H = 220;
    private static final double COL_GAP = 28;
    private static final double ROW_GAP = 230;
    private static final double PAD_X = 72;
    private static final double PAD_Y = 96;
    private static final double GROUP_GAP = 56;
    private static final double IMG_W = 148;
    private static final double IMG_H = 166;
    private static final double IMG_GAP = 16;

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Tool(name = "push_shot",
            description = "把单个分镜/镜头提示词推送到视频画布并创建视频生成节点（传 draft=true 时只建草稿不生成）。"
                    + "可传参考图公网 URL 或已上传到系统的图片 ID，提示词里用 @图片1 引用第一张图。")
    public JsonNode pushShot(
            @ToolParam(description = "视频提示词，必填，最多 2000 字") String prompt,
            @ToolParam(required = false, description = "镜头标题，不传则自动生成") String title,
            @ToolParam(required = false, description = "分组名，同组镜头在画布上横排显示") String groupId,
            @ToolParam(required = false, description = "组内镜号，从 1 开始，不传则自动递增") Integer shotIndex,
            @ToolParam(required = false, description = "参考图公网 URL 列表，按顺序对应 @图片1、@图片2") List<String> imageUrls,
            @ToolParam(required = false, description = "已上传到系统的图片 ID 列表") List<Long> imageIds,
            @ToolParam(required = false, description = "视频模型，形如 ark/doubao-seedance-2-0-260128") String model,
            @ToolParam(required = false, description = "画面比例，如 16:9、9:16") String ratio,
            @ToolParam(required = false, description = "时长（秒），需在模型支持范围内") Integer duration,
            @ToolParam(required = false, description = "清晰度，如 720P、1080P") String resolution,
            @ToolParam(required = false, description = "true 时只创建草稿节点，不立即生成") Boolean draft,
            @ToolParam(required = false, description = "工作空间 ID，默认 1") Long workspaceId) {
        ShotRequest request = new ShotRequest(prompt, title, groupId, shotIndex, imageUrls, imageIds,
                model, ratio, duration, resolution, draft);
        TaskNode node = taskService.createShot(McpToolSupport.workspace(workspaceId), request);
        return McpToolSupport.toJson(objectMapper, node);
    }

    @Tool(name = "push_storyboard",
            description = "批量推送一组分镜，入参为 JSON 数组字符串，每项可含 prompt/title/groupId/shotIndex/imageUrls/imageIds/model/ratio/duration/resolution。"
                    + "创建后会自动排版（同组每行 columns 个、超出折行）并保存画布快照。")
    public JsonNode pushStoryboard(
            @ToolParam(description = "分镜 JSON 数组字符串，例如 [{\"prompt\":\"海边日落空镜\"}]") String shotsJson,
            @ToolParam(required = false, description = "默认分组名，未在分镜中指定的使用该值") String groupId,
            @ToolParam(required = false, description = "每行镜头数，1-12，默认 4") Integer columns,
            @ToolParam(required = false, description = "工作空间 ID，默认 1") Long workspaceId) {
        List<ShotRequest> shots = parseShots(shotsJson);
        int perRow = columns == null ? 4 : columns;
        if (perRow < 1 || perRow > 12) {
            throw new BadRequestException("columns 必须是 1-12 的整数");
        }
        Long id = McpToolSupport.workspace(workspaceId);
        List<TaskNode> nodes = taskService.createStoryboard(id, new StoryboardRequest(shots, groupId));
        if (!nodes.isEmpty()) {
            try {
                arrangeLayout(id, nodes, perRow);
            } catch (Exception ignored) {
                // 排版失败不影响已创建的镜头，打开画布时会按默认规则布局
            }
        }
        return McpToolSupport.toJson(objectMapper, nodes);
    }

    @Tool(name = "update_shot_prompt",
            description = "修改已有镜头的提示词和生成参数；regenerate=true（默认）会立即重新提交生成，false 只保存。")
    public JsonNode updateShotPrompt(
            @ToolParam(description = "镜头任务 ID") Long taskId,
            @ToolParam(description = "新提示词，最多 2000 字") String prompt,
            @ToolParam(required = false, description = "视频模型，形如 ark/doubao-seedance-2-0-260128") String model,
            @ToolParam(required = false, description = "画面比例，如 16:9") String ratio,
            @ToolParam(required = false, description = "时长（秒）") Integer duration,
            @ToolParam(required = false, description = "清晰度，如 720P") String resolution,
            @ToolParam(required = false, description = "是否立即重新生成，默认 true") Boolean regenerate) {
        Long id = McpToolSupport.requireId(taskId, "镜头ID");
        TaskNode node = taskService.updatePrompt(id,
                new PromptUpdateRequest(prompt, regenerate, model, ratio, duration, resolution));
        return McpToolSupport.toJson(objectMapper, node);
    }

    @Tool(name = "retry_shot",
            description = "重新提交已有镜头的生成任务（使用当前提示词与参数）。")
    public JsonNode retryShot(@ToolParam(description = "镜头任务 ID") Long taskId) {
        return McpToolSupport.toJson(objectMapper, taskService.retry(McpToolSupport.requireId(taskId, "镜头ID")));
    }

    @Tool(name = "cancel_shot",
            description = "取消正在排队或生成中的镜头任务。")
    public JsonNode cancelShot(@ToolParam(description = "镜头任务 ID") Long taskId) {
        return McpToolSupport.toJson(objectMapper, taskService.cancel(McpToolSupport.requireId(taskId, "镜头ID")));
    }

    @Tool(name = "set_video_model",
            description = "只切换镜头模型并保存，不触发重新生成。")
    public JsonNode setVideoModel(
            @ToolParam(description = "镜头任务 ID") Long taskId,
            @ToolParam(description = "视频模型，形如 ark/doubao-seedance-2-0-260128") String model) {
        Long id = McpToolSupport.requireId(taskId, "镜头ID");
        String target = McpToolSupport.requireText(model, "请提供视频模型");
        TaskNode task = taskService.node(id);
        if (task.prompt() == null || task.prompt().isBlank()) {
            throw new BadRequestException("镜头 #" + id + " 没有可复用的提示词，请使用 update_shot_prompt 提供提示词");
        }
        TaskNode node = taskService.updatePrompt(id, new PromptUpdateRequest(
                task.prompt(), false, target, task.ratio(), task.duration(), task.resolution()));
        return McpToolSupport.toJson(objectMapper, node);
    }

    @Tool(name = "delete_shot",
            description = "删除镜头及其参考图连线。危险操作：必须先向用户确认镜头 ID，用户明确同意后再调用。")
    public JsonNode deleteShot(@ToolParam(description = "镜头任务 ID") Long taskId) {
        Long id = McpToolSupport.requireId(taskId, "镜头ID");
        taskService.deleteTask(id);
        return McpToolSupport.toJson(objectMapper, Map.of("ok", true, "taskId", id));
    }

    private List<ShotRequest> parseShots(String shotsJson) {
        McpToolSupport.requireText(shotsJson, "shotsJson 不能为空");
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

    // ---------------------------------------------------------------- 批量分镜自动排版

    /** 批量创建镜头后计算新节点坐标并写入画布快照，避免节点堆叠；版本冲突时重试一次 */
    private Map<String, Object> arrangeLayout(Long workspaceId, List<TaskNode> created, int columns) {
        double shotBlockH = SHOT_H + ROW_GAP;
        int perRow = Math.max(1, columns);
        int assigned = 0;

        for (int attempt = 0; attempt < 2; attempt++) {
            CanvasState data = taskService.getCanvas(workspaceId);
            ObjectNode snapshot = data.snapshot() != null && data.snapshot().isObject()
                    ? ((ObjectNode) data.snapshot()).deepCopy() : objectMapper.createObjectNode();
            JsonNode rawPositions = snapshot.get("positions");
            ObjectNode positions = rawPositions != null && rawPositions.isObject()
                    ? ((ObjectNode) rawPositions).deepCopy() : objectMapper.createObjectNode();

            Set<Long> createdIds = new LinkedHashSet<>();
            created.forEach(node -> createdIds.add(node.id()));
            List<Rect> placed = new ArrayList<>();
            assigned = 0;

            // 已有节点按已保存的坐标占位，新节点绕开它们
            for (TaskNode task : data.tasks()) {
                if (createdIds.contains(task.id())) {
                    continue;
                }
                double[] p = pos(positions.get("shot-" + task.id()));
                if (p != null) {
                    placed.add(new Rect(p[0], p[1], SHOT_W, shotBlockH, task.id(),
                            task.groupId() == null || task.groupId().isBlank() ? "default" : task.groupId()));
                }
            }
            for (ImageNode image : data.images()) {
                double[] p = pos(positions.get("image-" + image.id()));
                if (p == null) {
                    p = pos(image.x(), image.y());
                }
                if (p != null) {
                    placed.add(new Rect(p[0], p[1],
                            image.width() == null ? IMG_W : image.width(),
                            image.height() == null ? IMG_H : image.height(), null, null));
                }
            }
            for (CanvasNodeView node : data.nodes()) {
                double[] p = pos(positions.get("node-" + node.id()));
                if (p == null) {
                    p = pos(node.x(), node.y());
                }
                if (p != null) {
                    placed.add(new Rect(p[0], p[1],
                            node.width() == null ? 260 : node.width(),
                            node.height() == null ? 180 : node.height(), null, null));
                }
            }

            Map<String, Row> groupRows = new HashMap<>();
            for (TaskNode node : created) {
                String groupId = node.groupId() == null || node.groupId().isBlank() ? "default" : node.groupId();
                String key = "shot-" + node.id();
                double[] existing = pos(positions.get(key));
                if (existing != null) {
                    // 幂等命中的旧镜头保持原坐标，只参与占位
                    placed.add(new Rect(existing[0], existing[1], SHOT_W, shotBlockH, node.id(), groupId));
                    continue;
                }
                Row row = groupRows.get(groupId);
                if (row == null) {
                    List<Rect> rects = groupRects(placed, groupId);
                    if (!rects.isEmpty()) {
                        // 接在该组当前行末尾；若当前行已满则直接折行
                        Rect rightmost = rects.get(0);
                        for (Rect item : rects) {
                            if (item.x + item.w >= rightmost.x + rightmost.w) {
                                rightmost = item;
                            }
                        }
                        final Rect anchor = rightmost;
                        double baseX = rects.stream().mapToDouble(item -> item.x).min().orElse(PAD_X);
                        long count = rects.stream().filter(item -> item.y == anchor.y).count();
                        double bottom = rects.stream().mapToDouble(item -> item.y + item.h).max().orElse(anchor.y);
                        row = new Row(baseX, anchor.y, anchor.x + anchor.w + COL_GAP, count, bottom);
                    } else {
                        double startY = placed.isEmpty() ? PAD_Y : contentBottom(placed) + GROUP_GAP;
                        row = new Row(PAD_X, startY, PAD_X, 0, startY);
                    }
                    groupRows.put(groupId, row);
                }

                if (row.count >= perRow) {
                    // 折行：回到本组最左侧，在组内容下方另起一行
                    row.y = row.bottom + 20;
                    row.nextX = row.baseX;
                    row.count = 0;
                }

                Rect rect = freeSpot(placed, new Rect(row.nextX, row.y, SHOT_W, shotBlockH, node.id(), groupId),
                        SHOT_W + COL_GAP, null);
                positions.set(key, positionNode(rect.x, rect.y));
                placed.add(rect);
                row.nextX = rect.x + SHOT_W + COL_GAP;
                row.count += 1;
                row.bottom = Math.max(row.bottom, rect.y + shotBlockH);
                assigned += 1;
            }

            // 参考图：跟随所属镜头的实际坐标，放在镜头下方并横向错开
            for (TaskNode node : created) {
                double[] shotPos = pos(positions.get("shot-" + node.id()));
                if (shotPos == null) {
                    continue;
                }
                int index = 0;
                for (EdgeView edge : data.edges()) {
                    if (edge.taskId() == null || !edge.taskId().equals(node.id())) {
                        continue;
                    }
                    ImageNode image = data.images().stream()
                            .filter(item -> item.id().equals(edge.imageId()))
                            .findFirst().orElse(null);
                    if (image == null) {
                        continue;
                    }
                    String key = "image-" + image.id();
                    if (pos(positions.get(key)) != null || pos(image.x(), image.y()) != null) {
                        continue;
                    }
                    Rect rect = freeSpot(placed, new Rect(
                                    shotPos[0] + index * (IMG_W + IMG_GAP),
                                    shotPos[1] + SHOT_H + 20,
                                    image.width() == null ? IMG_W : image.width(),
                                    image.height() == null ? IMG_H : image.height(),
                                    null, null),
                            IMG_W + IMG_GAP, node.id());
                    positions.set(key, positionNode(rect.x, rect.y));
                    placed.add(rect);
                    assigned += 1;
                    index += 1;
                }
            }

            if (assigned == 0) {
                return Map.of("assigned", 0, "version", data.version() == null ? 0 : data.version());
            }

            snapshot.set("positions", positions);
            try {
                CanvasState updated = taskService.saveSnapshot(workspaceId, snapshot, data.version());
                return Map.of("assigned", assigned, "version", updated.version() == null ? 0 : updated.version());
            } catch (ConflictException e) {
                if (attempt == 0) {
                    continue;
                }
                return Map.of("assigned", assigned, "error", "快照版本冲突");
            }
        }
        return Map.of("assigned", assigned, "error", "快照版本冲突");
    }

    private List<Rect> groupRects(List<Rect> placed, String groupId) {
        List<Rect> rects = new ArrayList<>();
        for (Rect item : placed) {
            if (item.taskId != null && groupId.equals(item.groupId)) {
                rects.add(item);
            }
        }
        return rects;
    }

    private double contentBottom(List<Rect> placed) {
        double bottom = 0;
        for (Rect item : placed) {
            bottom = Math.max(bottom, item.y + item.h);
        }
        return bottom;
    }

    private Rect freeSpot(List<Rect> placed, Rect rect, double stepX, Long ignoreTaskId) {
        Rect candidate = new Rect(rect.x, rect.y, rect.w, rect.h, rect.taskId, rect.groupId);
        for (int guard = 0; guard < 200; guard++) {
            boolean hit = false;
            for (Rect item : placed) {
                if (ignoreTaskId != null && ignoreTaskId.equals(item.taskId)) {
                    continue;
                }
                if (overlap(candidate, item)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return candidate;
            }
            candidate.x += stepX;
        }
        return candidate;
    }

    private boolean overlap(Rect a, Rect b) {
        return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
    }

    private ObjectNode positionNode(double x, double y) {
        return objectMapper.createObjectNode().put("x", Math.round(x)).put("y", Math.round(y));
    }

    private double[] pos(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode x = node.get("x");
        JsonNode y = node.get("y");
        if (x == null || y == null || !x.isNumber() || !y.isNumber()) {
            return null;
        }
        return new double[]{x.asDouble(), y.asDouble()};
    }

    private double[] pos(Double x, Double y) {
        return x == null || y == null ? null : new double[]{x, y};
    }

    private static final class Rect {
        double x;
        double y;
        final double w;
        final double h;
        final Long taskId;
        final String groupId;

        Rect(double x, double y, double w, double h, Long taskId, String groupId) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.taskId = taskId;
            this.groupId = groupId;
        }
    }

    private static final class Row {
        final double baseX;
        double y;
        double nextX;
        long count;
        double bottom;

        Row(double baseX, double y, double nextX, long count, double bottom) {
            this.baseX = baseX;
            this.y = y;
            this.nextX = nextX;
            this.count = count;
            this.bottom = bottom;
        }
    }
}
