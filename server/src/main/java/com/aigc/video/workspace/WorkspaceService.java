package com.aigc.video.workspace;

import com.aigc.video.api.dto.WorkspaceView;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.CanvasNode;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.domain.TaskImageRef;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.CanvasNodeMapper;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.mapper.PromptRecordMapper;
import com.aigc.video.mapper.TaskImageRefMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.realtime.CanvasEvent;
import com.aigc.video.realtime.CanvasSocketRegistry;
import com.aigc.video.task.TaskStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 整个画布的聚合操作。 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    /** 复制后不再继续原任务，进行中的状态统一落为终态时的提示 */
    private static final String COPY_SETTLED_MESSAGE = "复制画布后需重新生成";

    private final WorkspaceMapper workspaceMapper;
    private final CanvasMapper canvasMapper;
    private final GenerationTaskMapper taskMapper;
    private final PromptRecordMapper promptRecordMapper;
    private final CanvasImageMapper imageMapper;
    private final CanvasNodeMapper nodeMapper;
    private final TaskImageRefMapper refMapper;
    private final CanvasSocketRegistry socketRegistry;
    private final ObjectMapper objectMapper;
    private final WorkspaceAccess workspaceAccess;
    private final ProjectService projectService;

    /** 画布列表：只返回当前用户的画布；传入项目时只返回该项目下的画布 */
    public List<WorkspaceView> list(Long projectId) {
        Long userId = com.aigc.video.user.UserContext.requireUserId();
        LambdaQueryWrapper<Workspace> query = Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getUserId, userId)
                .orderByAsc(Workspace::getId);        if (projectId != null) {
            projectService.requireOwned(projectId);
            query.eq(Workspace::getProjectId, projectId);
        }
        List<Workspace> workspaces = workspaceMapper.selectList(query);
        // 显式 select 字段，避免把 snapshot 大字段一起查出来
        Map<Long, Canvas> canvases = workspaces.isEmpty() ? Map.of() : canvasMapper.selectList(
                        Wrappers.<Canvas>lambdaQuery()
                                .select(Canvas::getId, Canvas::getWorkspaceId, Canvas::getVersion, Canvas::getUpdatedAt)
                                .in(Canvas::getWorkspaceId,
                                        workspaces.stream().map(Workspace::getId).toList()))
                .stream()
                .collect(Collectors.toMap(Canvas::getWorkspaceId, Function.identity(), (a, b) -> a));
        return workspaces.stream()
                .map(workspace -> toView(workspace, canvases.get(workspace.getId())))
                .toList();
    }

    /** 新建空白画布：同时创建 workspace 与 canvas，名称在所属项目内按序号生成 */
    @Transactional
    public WorkspaceView createWorkspace(Long projectId) {
        Long userId = com.aigc.video.user.UserContext.requireUserId();
        Long targetProject = projectId == null
                ? projectService.defaultProjectId(userId)
                : projectService.requireOwned(projectId).getId();
        long count = workspaceMapper.selectCount(Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getProjectId, targetProject));
        Workspace workspace = new Workspace();
        workspace.setUserId(userId);
        workspace.setProjectId(targetProject);
        workspace.setName("画布 " + (count + 1));
        workspace.setCreatedAt(LocalDateTime.now());
        workspaceMapper.insert(workspace);

        Canvas canvas = new Canvas();
        canvas.setWorkspaceId(workspace.getId());
        canvas.setUserId(userId);
        canvas.setVersion(0);
        canvas.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(canvas);
        return toView(workspace, canvas);
    }

    /** 按 ID 组装画布视图 */
    public WorkspaceView viewOf(Long workspaceId) {
        Workspace workspace = workspaceAccess.requireOwned(workspaceId);
        Canvas canvas = canvasMapper.selectOne(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, workspaceId));
        return toView(workspace, canvas);
    }

    /** 重命名画布 */
    @Transactional
    public void renameWorkspace(Long workspaceId, String name) {
        Workspace workspace = workspaceAccess.requireOwned(workspaceId);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("画布名称不能为空");
        }
        if (trimmed.length() > 128) {
            throw new BadRequestException("画布名称不能超过 128 个字符");
        }
        workspaceMapper.update(null, Wrappers.<Workspace>lambdaUpdate()
                .eq(Workspace::getId, workspace.getId())
                .set(Workspace::getName, trimmed));
        broadcastChanged(workspaceId);
    }

    /**
     * 复制画布：workspace / canvas 快照 / 镜头 / 图片 / 节点 / 连线 / 提示词记录全部深拷贝。
     * 复制出的镜头不继承供应商任务号，进行中的状态统一落为终态，避免轮询到原任务。
     */
    @Transactional
    public Workspace copyWorkspace(Long workspaceId) {
        Workspace source = workspaceAccess.requireOwned(workspaceId);
        Long userId = source.getUserId() != null
                ? source.getUserId() : com.aigc.video.user.UserContext.requireUserId();
        LocalDateTime now = LocalDateTime.now();

        Workspace copy = new Workspace();
        copy.setUserId(userId);
        copy.setProjectId(source.getProjectId() != null
                ? source.getProjectId() : projectService.defaultProjectId(userId));
        copy.setName(source.getName() + " 副本");
        copy.setCreatedAt(now);
        workspaceMapper.insert(copy);
        Long copyId = copy.getId();

        Canvas canvas = new Canvas();
        canvas.setWorkspaceId(copyId);
        canvas.setUserId(userId);
        canvas.setVersion(0);
        canvas.setUpdatedAt(now);
        canvasMapper.insert(canvas);

        // 图片先复制，保留旧 ID -> 新 ID 映射，供连线重建
        Map<Long, Long> imageIds = new HashMap<>();
        for (CanvasImage original : imageMapper.selectList(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, workspaceId))) {
            CanvasImage image = new CanvasImage();
            image.setUserId(userId);
            image.setWorkspaceId(copyId);
            image.setCanvasId(canvas.getId());
            image.setName(original.getName());
            image.setUrl(original.getUrl());
            image.setObjectKey(original.getObjectKey());
            image.setX(original.getX());
            image.setY(original.getY());
            image.setWidth(original.getWidth());
            image.setHeight(original.getHeight());
            image.setPrompt(original.getPrompt());
            image.setProvider(original.getProvider());
            image.setSource(original.getSource());
            boolean active = TaskStatus.isActive(original.getStatus());
            image.setStatus(active ? "FAILED" : original.getStatus());
            image.setErrorMsg(active ? COPY_SETTLED_MESSAGE : original.getErrorMsg());
            image.setCreatedAt(now);
            imageMapper.insert(image);
            imageIds.put(original.getId(), image.getId());
        }

        List<GenerationTask> tasks = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getWorkspaceId, workspaceId));
        Map<Long, Long> taskIds = new HashMap<>();
        for (GenerationTask original : tasks) {
            GenerationTask task = new GenerationTask();
            task.setUserId(userId);
            task.setWorkspaceId(copyId);
            task.setCanvasId(canvas.getId());
            task.setGroupId(original.getGroupId());
            task.setShotIndex(original.getShotIndex());
            task.setTitle(original.getTitle());
            task.setPrompt(original.getPrompt());
            task.setModel(original.getModel());
            task.setRatio(original.getRatio());
            task.setDuration(original.getDuration());
            task.setResolution(original.getResolution());
            task.setProvider(original.getProvider());
            task.setVideoUrl(original.getVideoUrl());
            task.setCoverUrl(original.getCoverUrl());
            boolean active = TaskStatus.isActive(original.getStatus());
            task.setStatus(active ? TaskStatus.CANCELED.name() : original.getStatus());
            task.setProgress(active ? 0 : original.getProgress());
            task.setErrorMsg(active ? COPY_SETTLED_MESSAGE : original.getErrorMsg());
            task.setPollCount(0);
            task.setSubmittedAt(original.getSubmittedAt());
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            taskIds.put(original.getId(), task.getId());

            for (TaskImageRef originalRef : refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                    .eq(TaskImageRef::getTaskId, original.getId()))) {
                Long imageId = imageIds.get(originalRef.getImageId());
                if (imageId == null) {
                    continue;
                }
                TaskImageRef ref = new TaskImageRef();
                ref.setUserId(userId);
                ref.setTaskId(task.getId());
                ref.setImageId(imageId);
                ref.setSortIndex(originalRef.getSortIndex());
                ref.setCreatedAt(now);
                refMapper.insert(ref);
            }

            for (PromptRecord originalRecord : promptRecordMapper.selectList(Wrappers.<PromptRecord>lambdaQuery()
                    .eq(PromptRecord::getTaskId, original.getId()))) {
                PromptRecord record = new PromptRecord();
                record.setUserId(userId);
                record.setWorkspaceId(copyId);
                record.setTaskId(task.getId());
                record.setPrompt(originalRecord.getPrompt());
                record.setCreatedAt(originalRecord.getCreatedAt());
                promptRecordMapper.insert(record);
            }
        }

        Map<Long, Long> nodeIds = new HashMap<>();
        for (CanvasNode original : nodeMapper.selectList(Wrappers.<CanvasNode>lambdaQuery()
                .eq(CanvasNode::getWorkspaceId, workspaceId))) {
            CanvasNode node = new CanvasNode();
            node.setUserId(userId);
            node.setWorkspaceId(copyId);
            node.setNodeType(original.getNodeType());
            node.setTitle(original.getTitle());
            node.setPrompt(original.getPrompt());
            node.setContent(original.getContent());
            boolean active = TaskStatus.isActive(original.getStatus());
            node.setStatus(active ? "FAILED" : original.getStatus());
            node.setErrorMsg(active ? COPY_SETTLED_MESSAGE : original.getErrorMsg());
            node.setX(original.getX());
            node.setY(original.getY());
            node.setWidth(original.getWidth());
            node.setHeight(original.getHeight());
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            nodeMapper.insert(node);
            nodeIds.put(original.getId(), node.getId());
        }

        // 快照里的节点坐标 / 连线按旧 ID 记录，复制后需要重映射到新 ID
        Canvas sourceCanvas = canvasMapper.selectOne(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, workspaceId));
        if (sourceCanvas != null && sourceCanvas.getSnapshot() != null) {
            Map<String, String> nodeKeys = new HashMap<>();
            taskIds.forEach((oldId, newId) -> nodeKeys.put("shot-" + oldId, "shot-" + newId));
            imageIds.forEach((oldId, newId) -> nodeKeys.put("image-" + oldId, "image-" + newId));
            nodeIds.forEach((oldId, newId) -> nodeKeys.put("node-" + oldId, "node-" + newId));
            canvasMapper.update(null, Wrappers.<Canvas>lambdaUpdate()
                    .eq(Canvas::getId, canvas.getId())
                    .set(Canvas::getSnapshot, remapSnapshot(sourceCanvas.getSnapshot(), nodeKeys)));
        }

        broadcastChanged(workspaceId);
        return copy;
    }

    /** 把快照中的节点坐标与连线重映射到复制后的节点 ID；解析失败时保留原快照 */
    private String remapSnapshot(String snapshot, Map<String, String> nodeKeys) {
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            if (!root.isObject()) {
                return snapshot;
            }
            ObjectNode copy = ((ObjectNode) root).deepCopy();
            JsonNode positions = root.get("positions");
            if (positions != null && positions.isObject()) {
                ObjectNode mapped = objectMapper.createObjectNode();
                positions.fields().forEachRemaining(entry ->
                        mapped.set(nodeKeys.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()));
                copy.set("positions", mapped);
            }
            JsonNode links = root.get("links");
            if (links != null && links.isArray()) {
                ArrayNode mapped = objectMapper.createArrayNode();
                for (JsonNode link : links) {
                    ObjectNode item = ((ObjectNode) link).deepCopy();
                    String source = item.path("source").asText();
                    String target = item.path("target").asText();
                    item.put("source", nodeKeys.getOrDefault(source, source));
                    item.put("target", nodeKeys.getOrDefault(target, target));
                    mapped.add(item);
                }
                copy.set("links", mapped);
            }
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return snapshot;
        }
    }

    /** 删除画布及其全部关联数据；系统始终至少保留一个画布。 */
    @Transactional
    public void deleteWorkspace(Long workspaceId) {
        workspaceAccess.requireOwned(workspaceId);
        Long userId = com.aigc.video.user.UserContext.requireUserId();
        if (workspaceMapper.selectCount(Wrappers.<Workspace>lambdaQuery()
                .and(w -> w.eq(Workspace::getUserId, userId).or().isNull(Workspace::getUserId))) <= 1) {
            throw new BadRequestException("至少保留一个画布，无法删除");
        }

        List<Long> taskIds = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                        .select(GenerationTask::getId)
                        .eq(GenerationTask::getWorkspaceId, workspaceId))
                .stream()
                .map(GenerationTask::getId)
                .toList();
        if (!taskIds.isEmpty()) {
            refMapper.delete(Wrappers.<TaskImageRef>lambdaQuery().in(TaskImageRef::getTaskId, taskIds));
        }
        taskMapper.delete(Wrappers.<GenerationTask>lambdaQuery().eq(GenerationTask::getWorkspaceId, workspaceId));
        promptRecordMapper.delete(Wrappers.<PromptRecord>lambdaQuery().eq(PromptRecord::getWorkspaceId, workspaceId));
        imageMapper.delete(Wrappers.<CanvasImage>lambdaQuery().eq(CanvasImage::getWorkspaceId, workspaceId));
        nodeMapper.delete(Wrappers.<CanvasNode>lambdaQuery().eq(CanvasNode::getWorkspaceId, workspaceId));
        canvasMapper.delete(Wrappers.<Canvas>lambdaQuery().eq(Canvas::getWorkspaceId, workspaceId));
        workspaceMapper.deleteById(workspaceId);

        socketRegistry.broadcast(workspaceId,
                CanvasEvent.of("workspace.deleted", Map.of("workspaceId", workspaceId)));
    }

    private WorkspaceView toView(Workspace workspace, Canvas canvas) {
        return new WorkspaceView(
                workspace.getId(),
                workspace.getName(),
                canvas == null ? null : canvas.getId(),
                canvas == null ? 0 : canvas.getVersion(),
                canvas == null ? null : canvas.getUpdatedAt(),
                workspace.getProjectId());
    }

    /** 画布名称或数量变化，通知其他标签页刷新切换列表 */
    private void broadcastChanged(Long workspaceId) {
        socketRegistry.broadcast(workspaceId,
                CanvasEvent.of("workspace.changed", Map.of("workspaceId", workspaceId)));
    }
}
