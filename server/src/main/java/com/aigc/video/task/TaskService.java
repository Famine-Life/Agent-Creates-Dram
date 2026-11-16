package com.aigc.video.task;

import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.api.dto.CanvasState;
import com.aigc.video.api.dto.EdgeRequest;
import com.aigc.video.api.dto.EdgeView;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.ImageRequest;
import com.aigc.video.api.dto.OrderRequest;
import com.aigc.video.api.dto.PromptUpdateRequest;
import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.StoryboardRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.ConflictException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.common.TooManyRequestsException;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.domain.TaskImageRef;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.mapper.PromptRecordMapper;
import com.aigc.video.mapper.TaskImageRefMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.provider.ProviderRegistry;
import com.aigc.video.provider.VideoContext;
import com.aigc.video.realtime.CanvasEvent;
import com.aigc.video.realtime.CanvasSocketRegistry;
import com.aigc.video.storage.AssetService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 画布 / 任务 / 图片 / 连线的全部业务逻辑 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    @Value("${aigc.dispatch.max-queued-tasks:1000}")
    private int maxQueuedTasks;

    public static final String DEFAULT_GROUP = "默认分组";

    private static final double IMAGE_W = 148;
    private static final double IMAGE_H = 166;

    private final GenerationTaskMapper taskMapper;
    private final PromptRecordMapper promptRecordMapper;
    private final CanvasImageMapper imageMapper;
    private final TaskImageRefMapper refMapper;
    private final CanvasMapper canvasMapper;
    private final WorkspaceMapper workspaceMapper;
    private final CanvasSocketRegistry socketRegistry;
    private final ModelCatalog modelCatalog;
    private final ProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final CanvasNodeService canvasNodeService;
    private final AssetService assetService;

    // ---------------------------------------------------------------- 画布读取

    public CanvasState getCanvas(Long workspaceId) {
        Canvas canvas = requireCanvas(workspaceId);
        List<GenerationTask> tasks = orderedTasks(workspaceId);
        List<TaskImageRef> refs = refsOf(tasks);
        List<CanvasImage> images = imageMapper.selectList(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, workspaceId)
                .orderByAsc(CanvasImage::getId));
        return new CanvasState(
                workspaceId,
                canvas.getId(),
                canvas.getVersion(),
                parseSnapshot(canvas.getSnapshot()),
                buildNodes(tasks, refs),
                images.stream().map(this::toImageNode).toList(),
                canvasNodeService.list(workspaceId),
                refs.stream().map(this::toEdgeView).toList()
        );
    }

    public List<PromptRecord> listPromptRecords(Long workspaceId) {
        requireCanvas(workspaceId);
        return promptRecordMapper.selectList(Wrappers.<PromptRecord>lambdaQuery()
                .eq(PromptRecord::getWorkspaceId, workspaceId)
                .orderByDesc(PromptRecord::getId)
                .last("LIMIT 200"));
    }

    // ---------------------------------------------------------------- 镜头任务

    @Transactional
    public TaskNode createShot(Long workspaceId, ShotRequest request) {
        if (request == null) {
            throw new BadRequestException("请求不能为空");
        }
        boolean draft = Boolean.TRUE.equals(request.draft())
                || request.prompt() == null || request.prompt().isBlank();
        if (!draft && request.prompt().length() > 2000) {
            throw new BadRequestException("提示词不能超过 2000 字");
        }
        Canvas canvas = requireCanvas(workspaceId);
        String groupId = normalizeGroup(request.groupId());
        int shotIndex = request.shotIndex() != null && request.shotIndex() > 0
                ? request.shotIndex()
                : nextShotIndex(workspaceId, groupId);
        // 草稿节点不参与幂等：空提示词可能被多次创建，后续由用户填写后再生成
        if (!draft) {
            String idem = idemKey(workspaceId, groupId, shotIndex, request.prompt());
            GenerationTask existing = findByIdem(idem);
            if (existing != null) {
                log.info("幂等命中，复用任务 id={}", existing.getId());
                attachImages(existing, resolveImages(workspaceId, canvas.getId(), request));
                recordPrompt(workspaceId, existing.getId(), request.prompt());
                return node(existing.getId());
            }
        }

        if (!draft) ensureQueueCapacity();

        GenerationTask task = new GenerationTask();
        task.setWorkspaceId(workspaceId);
        task.setCanvasId(canvas.getId());
        task.setGroupId(groupId);
        task.setShotIndex(shotIndex);
        task.setTitle(defaultTitle(request.title(), shotIndex));
        task.setPrompt(request.prompt() == null ? "" : request.prompt().trim());
        ModelCatalog.ResolvedModel video = modelCatalog.resolve(ModelCatalog.Capability.VIDEO, request.model());
        task.setModel(video.modelId());
        task.setProvider(video.providerKey());
        task.setRatio(normalizeRatio(request.ratio(), video));
        task.setDuration(normalizeDuration(request.duration(), video));
        task.setResolution(normalizeResolution(request.resolution(), video));
        // 草稿节点不进入调度队列，等用户填写提示词后由 updatePrompt 触发 resetForRegenerate
        task.setStatus(draft ? TaskStatus.IDLE.name() : TaskStatus.QUEUED.name());
        task.setProgress(0);
        task.setPollCount(0);
        if (!draft) {
            task.setIdemKey(idemKey(workspaceId, groupId, shotIndex, request.prompt()));
        }
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        attachImages(task, resolveImages(workspaceId, canvas.getId(), request));
        if (!draft) {
            recordPrompt(workspaceId, task.getId(), request.prompt());
        }

        TaskNode node = node(task.getId());
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("node.created", node));
        return node;
    }

    @Transactional
    public List<TaskNode> createStoryboard(Long workspaceId, StoryboardRequest request) {
        if (request == null || request.shots() == null || request.shots().isEmpty()) {
            throw new BadRequestException("分镜列表不能为空");
        }
        String groupId = normalizeGroup(request.groupId());
        List<TaskNode> result = new ArrayList<>();
        for (ShotRequest shot : request.shots()) {
            if (shot == null || shot.prompt() == null || shot.prompt().isBlank()) {
                continue;
            }
            ShotRequest merged = new ShotRequest(
                    shot.prompt(),
                    shot.title(),
                    shot.groupId() == null || shot.groupId().isBlank() ? groupId : shot.groupId(),
                    shot.shotIndex(),
                    shot.imageUrls(),
                    shot.imageIds(),
                    shot.model(),
                    shot.ratio(),
                    shot.duration(),
                    shot.resolution(),
                    // 脚本「添加到画布」只落草稿节点，由用户确认后再生成
                    Boolean.TRUE);
            result.add(createShot(workspaceId, merged));
        }
        return result;
    }

    @Transactional
    public TaskNode updatePrompt(Long taskId, PromptUpdateRequest request) {
        GenerationTask task = requireTask(taskId);
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            throw new BadRequestException("提示词不能为空");
        }
        if (request.prompt().length() > 2000) {
            throw new BadRequestException("提示词不能超过 2000 字");
        }
        LocalDateTime now = LocalDateTime.now();
        String requested = request.model() == null || request.model().isBlank()
                ? modelCatalog.taskKey(task.getProvider(), task.getModel()) : request.model();
        ModelCatalog.ResolvedModel video = modelCatalog.resolve(ModelCatalog.Capability.VIDEO, requested);
        LambdaUpdateWrapper<GenerationTask> update = Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .set(GenerationTask::getPrompt, request.prompt().trim())
                .set(GenerationTask::getModel, video.modelId())
                .set(GenerationTask::getProvider, video.providerKey())
                .set(GenerationTask::getUpdatedAt, now);
        if (request.ratio() != null && !request.ratio().isBlank()) {
            update.set(GenerationTask::getRatio, normalizeRatio(request.ratio(), video));
        }
        if (request.duration() != null) {
            update.set(GenerationTask::getDuration, normalizeDuration(request.duration(), video));
        }
        // 清晰度与模型能力绑定：显式传值时按新模型校验，未传时沿用旧值并按新模型兜底
        update.set(GenerationTask::getResolution,
                normalizeResolution(request.resolution() == null || request.resolution().isBlank()
                        ? task.getResolution() : request.resolution(), video));
        taskMapper.update(null, update);
        recordPrompt(task.getWorkspaceId(), taskId, request.prompt());
        if (request.regenerate() == null || request.regenerate()) {
            resetForRegenerate(taskId);
        }
        broadcastTask("node.updated", taskId);
        return node(taskId);
    }

    private void recordPrompt(Long workspaceId, Long taskId, String prompt) {
        PromptRecord record = new PromptRecord();
        record.setWorkspaceId(workspaceId);
        record.setTaskId(taskId);
        record.setPrompt(prompt.trim());
        record.setCreatedAt(LocalDateTime.now());
        promptRecordMapper.insert(record);
    }

    @Transactional
    public TaskNode retry(Long taskId) {
        requireTask(taskId);
        resetForRegenerate(taskId);
        broadcastTask("node.updated", taskId);
        return node(taskId);
    }

    @Transactional
    public TaskNode cancel(Long taskId) {
        GenerationTask task = requireTask(taskId);
        if (!TaskStatus.isActive(task.getStatus())) {
            throw new BadRequestException("当前状态不可取消：" + task.getStatus());
        }
        cancelProviderTask(task);
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .set(GenerationTask::getStatus, TaskStatus.CANCELED.name())
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
        broadcastTask("node.updated", taskId);
        return node(taskId);
    }

    /** 尽力取消供应商侧任务；失败只记录日志，本地状态仍按已取消处理 */
    private void cancelProviderTask(GenerationTask task) {
        String providerTaskId = task.getProviderTaskId();
        if (providerTaskId == null || providerTaskId.isBlank()) {
            return;
        }
        try {
            ModelCatalog.ResolvedModel model = modelCatalog.resolveVideoTask(task.getProvider(), task.getModel());
            providerRegistry.get(model.protocol()).cancel(providerTaskId,
                    new VideoContext(model.providerKey(), model.baseUrl(), model.apiKey(),
                            model.modelId(), model.watermark()));
        } catch (Exception e) {
            log.warn("供应商取消失败 id={} providerTaskId={}：{}", task.getId(), providerTaskId, e.getMessage());
        }
    }

    @Transactional
    public void deleteTask(Long taskId) {
        GenerationTask task = requireTask(taskId);
        refMapper.delete(Wrappers.<TaskImageRef>lambdaQuery().eq(TaskImageRef::getTaskId, taskId));
        taskMapper.deleteById(taskId);
        socketRegistry.broadcast(task.getWorkspaceId(),
                CanvasEvent.of("node.deleted", Map.of("taskId", taskId)));
    }

    @Transactional
    public CanvasState reorder(Long workspaceId, OrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return getCanvas(workspaceId);
        }
        LocalDateTime now = LocalDateTime.now();
        for (OrderRequest.OrderItem item : request.items()) {
            if (item == null || item.taskId() == null) {
                continue;
            }
            LambdaUpdateWrapper<GenerationTask> update = Wrappers.<GenerationTask>lambdaUpdate()
                    .eq(GenerationTask::getId, item.taskId())
                    .eq(GenerationTask::getWorkspaceId, workspaceId)
                    .set(GenerationTask::getUpdatedAt, now);
            if (item.groupId() != null && !item.groupId().isBlank()) {
                update.set(GenerationTask::getGroupId, item.groupId());
            }
            if (item.shotIndex() != null && item.shotIndex() > 0) {
                update.set(GenerationTask::getShotIndex, item.shotIndex());
            }
            taskMapper.update(null, update);
        }
        return getCanvas(workspaceId);
    }

    // ---------------------------------------------------------------- 画布快照

    @Transactional
    public CanvasState saveSnapshot(Long workspaceId, JsonNode snapshot, Integer baseVersion) {
        Canvas canvas = requireCanvas(workspaceId);
        String text = snapshot == null || snapshot.isNull() ? null : snapshot.toString();
        LambdaUpdateWrapper<Canvas> update = Wrappers.<Canvas>lambdaUpdate()
                .eq(Canvas::getId, canvas.getId())
                .set(Canvas::getSnapshot, text)
                .set(Canvas::getVersion, (canvas.getVersion() == null ? 0 : canvas.getVersion()) + 1)
                .set(Canvas::getUpdatedAt, LocalDateTime.now());
        if (baseVersion != null) {
            // 乐观锁：版本不一致说明其他标签页/实例已保存过
            update.eq(Canvas::getVersion, baseVersion);
            if (canvasMapper.update(null, update) == 0) {
                throw new ConflictException("画布已在其他窗口更新，请刷新后重试");
            }
        } else {
            canvasMapper.update(null, update);
        }
        return getCanvas(workspaceId);
    }

    // ---------------------------------------------------------------- 图片节点

    @Transactional
    public ImageNode createImage(Long workspaceId, ImageRequest request) {
        if (request == null || request.url() == null || request.url().isBlank()) {
            throw new BadRequestException("图片地址不能为空");
        }
        Canvas canvas = requireCanvas(workspaceId);
        long count = imageMapper.selectCount(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, workspaceId));

        CanvasImage image = new CanvasImage();
        image.setWorkspaceId(workspaceId);
        image.setCanvasId(canvas.getId());
        image.setName(defaultImageName(request.name(), count + 1));
        image.setUrl(request.url());
        image.setObjectKey(request.objectKey());
        // x/y 为空时由前端自动排布，避免与镜头行重叠
        image.setX(request.x());
        image.setY(request.y());
        image.setWidth(request.width() == null ? IMAGE_W : request.width());
        image.setHeight(request.height() == null ? IMAGE_H : request.height());
        // 复制节点时保留生成结果与提示词，普通上传则只有地址
        image.setPrompt(request.prompt());
        image.setStatus(request.status());
        image.setSource(ImageNode.SOURCE_AI.equals(request.source())
                ? ImageNode.SOURCE_AI : ImageNode.SOURCE_UPLOAD);
        image.setCreatedAt(LocalDateTime.now());
        imageMapper.insert(image);

        ImageNode node = toImageNode(image);
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("image.created", node));
        return node;
    }

    @Transactional
    public ImageNode updateImage(Long workspaceId, Long imageId, ImageRequest request) {
        CanvasImage image = requireImage(workspaceId, imageId);
        CanvasImage sourceImage = request.sourceImageId() == null
                ? null : requireImage(workspaceId, request.sourceImageId());
        LambdaUpdateWrapper<CanvasImage> update = Wrappers.<CanvasImage>lambdaUpdate()
                .eq(CanvasImage::getId, image.getId());
        if (sourceImage != null) {
            update.set(CanvasImage::getName, sourceImage.getName())
                    .set(CanvasImage::getUrl, sourceImage.getUrl())
                    .set(CanvasImage::getObjectKey, sourceImage.getObjectKey())
                    .set(CanvasImage::getSource, sourceImage.getSource())
                    .set(CanvasImage::getPrompt, sourceImage.getPrompt())
                    .set(CanvasImage::getStatus, sourceImage.getStatus());
        }
        if (sourceImage == null && request.name() != null && !request.name().isBlank()) {
            update.set(CanvasImage::getName, request.name());
        }
        if (sourceImage == null && request.url() != null && !request.url().isBlank()) {
            update.set(CanvasImage::getUrl, request.url());
        }
        if (sourceImage == null && request.objectKey() != null) {
            update.set(CanvasImage::getObjectKey, request.objectKey());
        }
        if (sourceImage == null && request.source() != null && !request.source().isBlank()) {
            update.set(CanvasImage::getSource, request.source());
        }
        if (request.x() != null) {
            update.set(CanvasImage::getX, request.x());
        }
        if (request.y() != null) {
            update.set(CanvasImage::getY, request.y());
        }
        if (request.width() != null) {
            update.set(CanvasImage::getWidth, request.width());
        }
        if (request.height() != null) {
            update.set(CanvasImage::getHeight, request.height());
        }
        if (request.prompt() != null) {
            update.set(CanvasImage::getPrompt, request.prompt());
        }
        // MyBatis Plus rejects UPDATE statements with no SET clause. Older clients may
        // send fields this server version does not understand; leave the node unchanged.
        if (sourceImage == null
                && (request.name() == null || request.name().isBlank())
                && (request.url() == null || request.url().isBlank())
                && request.objectKey() == null
                && (request.source() == null || request.source().isBlank())
                && request.x() == null && request.y() == null
                && request.width() == null && request.height() == null
                && request.prompt() == null) {
            return toImageNode(image);
        }
        imageMapper.update(null, update);

        CanvasImage latest = imageMapper.selectById(imageId);
        if (latest != null) {
            socketRegistry.broadcast(workspaceId, CanvasEvent.of("image.updated", toImageNode(latest)));
        }
        return toImageNode(latest == null ? image : latest);
    }

    @Transactional
    public void deleteImage(Long workspaceId, Long imageId) {
        CanvasImage image = requireImage(workspaceId, imageId);
        List<TaskImageRef> refs = refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getImageId, imageId));
        Set<Long> affectedTasks = refs.stream().map(TaskImageRef::getTaskId).collect(Collectors.toSet());
        refMapper.delete(Wrappers.<TaskImageRef>lambdaQuery().eq(TaskImageRef::getImageId, imageId));
        imageMapper.deleteById(image.getId());
        socketRegistry.broadcast(workspaceId,
                CanvasEvent.of("image.deleted", Map.of("imageId", imageId)));
        affectedTasks.forEach(taskId -> broadcastTask("node.updated", taskId));
    }

    // ---------------------------------------------------------------- 连线

    @Transactional
    public EdgeView createEdge(Long workspaceId, EdgeRequest request) {
        if (request == null || request.taskId() == null || request.imageId() == null) {
            throw new BadRequestException("连线需要同时指定镜头与图片");
        }
        GenerationTask task = requireTask(request.taskId());
        if (!Objects.equals(task.getWorkspaceId(), workspaceId)) {
            throw new NotFoundException("镜头不存在：" + request.taskId());
        }
        CanvasImage image = requireImage(workspaceId, request.imageId());

        TaskImageRef existing = refMapper.selectOne(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, task.getId())
                .eq(TaskImageRef::getImageId, image.getId())
                .last("LIMIT 1"));
        if (existing != null) {
            return toEdgeView(existing);
        }

        TaskImageRef ref = new TaskImageRef();
        ref.setTaskId(task.getId());
        ref.setImageId(image.getId());
        ref.setSortIndex(nextSortIndex(task.getId()));
        ref.setCreatedAt(LocalDateTime.now());
        refMapper.insert(ref);

        EdgeView view = toEdgeView(ref);
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("edge.created", view));
        broadcastTask("node.updated", task.getId());
        return view;
    }

    @Transactional
    public void deleteEdge(Long workspaceId, Long edgeId) {
        TaskImageRef ref = refMapper.selectById(edgeId);
        if (ref == null) {
            throw new NotFoundException("连线不存在：" + edgeId);
        }
        refMapper.deleteById(edgeId);
        // 保持剩余引用的序号连续，保证 @图片1、@图片2 顺序稳定
        List<TaskImageRef> rest = refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, ref.getTaskId())
                .orderByAsc(TaskImageRef::getSortIndex)
                .orderByAsc(TaskImageRef::getId));
        for (int i = 0; i < rest.size(); i++) {
            TaskImageRef item = rest.get(i);
            if (item.getSortIndex() == null || item.getSortIndex() != i) {
                refMapper.update(null, Wrappers.<TaskImageRef>lambdaUpdate()
                        .eq(TaskImageRef::getId, item.getId())
                        .set(TaskImageRef::getSortIndex, i));
            }
        }
        socketRegistry.broadcast(workspaceId,
                CanvasEvent.of("edge.deleted", Map.of("edgeId", edgeId, "taskId", ref.getTaskId())));
        broadcastTask("node.updated", ref.getTaskId());
    }

    // ---------------------------------------------------------------- 对外辅助

    public TaskNode node(Long taskId) {
        GenerationTask task = requireTask(taskId);
        return buildNodes(List.of(task), refsOf(List.of(task))).get(0);
    }

    public void broadcastTask(String type, Long taskId) {
        try {
            TaskNode node = node(taskId);
            socketRegistry.broadcast(node.workspaceId(), CanvasEvent.of(type, node));
        } catch (Exception e) {
            log.warn("广播任务事件失败 type={} taskId={}：{}", type, taskId, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部实现

    private void resetForRegenerate(Long taskId) {
        GenerationTask current = requireTask(taskId);
        if (!TaskStatus.QUEUED.name().equals(current.getStatus())
                && !TaskStatus.RECEIVED.name().equals(current.getStatus())) {
            ensureQueueCapacity();
        }
        taskMapper.update(null, Wrappers.<GenerationTask>lambdaUpdate()
                .eq(GenerationTask::getId, taskId)
                .set(GenerationTask::getStatus, TaskStatus.QUEUED.name())
                .set(GenerationTask::getProgress, 0)
                .set(GenerationTask::getProviderTaskId, null)
                .set(GenerationTask::getErrorMsg, null)
                .set(GenerationTask::getPollCount, 0)
                .set(GenerationTask::getNextPollAt, null)
                .set(GenerationTask::getSubmittedAt, null)
                .set(GenerationTask::getVideoUrl, null)
                .set(GenerationTask::getCoverUrl, null)
                .set(GenerationTask::getUpdatedAt, LocalDateTime.now()));
    }

    private void ensureQueueCapacity() {
        long queuedCount = taskMapper.selectCount(Wrappers.<GenerationTask>lambdaQuery()
                .in(GenerationTask::getStatus, TaskStatus.RECEIVED.name(), TaskStatus.QUEUED.name()));
        if (queuedCount >= maxQueuedTasks) {
            throw new TooManyRequestsException("生成队列已满，请稍后再提交（上限 " + maxQueuedTasks + " 个待提交任务）");
        }
    }

    /** 按图片顺序补齐引用，已存在的跳过 */
    private void attachImages(GenerationTask task, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        List<TaskImageRef> existing = refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, task.getId()));
        Set<Long> have = existing.stream().map(TaskImageRef::getImageId).collect(Collectors.toCollection(HashSet::new));
        int next = existing.stream()
                .map(TaskImageRef::getSortIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        for (Long imageId : imageIds) {
            if (imageId == null || !have.add(imageId)) {
                continue;
            }
            TaskImageRef ref = new TaskImageRef();
            ref.setTaskId(task.getId());
            ref.setImageId(imageId);
            ref.setSortIndex(next++);
            ref.setCreatedAt(LocalDateTime.now());
            refMapper.insert(ref);
            socketRegistry.broadcast(task.getWorkspaceId(), CanvasEvent.of("edge.created", toEdgeView(ref)));
        }
    }

    private List<Long> resolveImages(Long workspaceId, Long canvasId, ShotRequest request) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (request.imageIds() != null) {
            for (Long id : request.imageIds()) {
                if (id == null) {
                    continue;
                }
                CanvasImage image = imageMapper.selectById(id);
                if (image == null || !Objects.equals(image.getWorkspaceId(), workspaceId)) {
                    throw new BadRequestException("参考图不存在：" + id);
                }
                result.add(id);
            }
        }
        if (request.imageUrls() != null) {
            for (String url : request.imageUrls()) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                CanvasImage found = imageMapper.selectOne(Wrappers.<CanvasImage>lambdaQuery()
                        .eq(CanvasImage::getWorkspaceId, workspaceId)
                        .eq(CanvasImage::getUrl, url)
                        .last("LIMIT 1"));
                if (found == null) {
                    ImageNode created = createImage(workspaceId,
                            new ImageRequest(null, url, null, null, null, null, null, null, null, null, null));
                    found = imageMapper.selectById(created.id());
                }
                result.add(found.getId());
            }
        }
        return new ArrayList<>(result);
    }

    private List<GenerationTask> orderedTasks(Long workspaceId) {
        List<GenerationTask> all = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getWorkspaceId, workspaceId)
                .orderByAsc(GenerationTask::getId));
        Map<String, List<GenerationTask>> byGroup = new LinkedHashMap<>();
        for (GenerationTask task : all) {
            byGroup.computeIfAbsent(normalizeGroup(task.getGroupId()), k -> new ArrayList<>()).add(task);
        }
        List<GenerationTask> ordered = new ArrayList<>(all.size());
        for (List<GenerationTask> group : byGroup.values()) {
            group.sort(Comparator
                    .comparing((GenerationTask t) -> t.getShotIndex() == null ? Integer.MAX_VALUE : t.getShotIndex())
                    .thenComparing(GenerationTask::getId));
            ordered.addAll(group);
        }
        return ordered;
    }

    private List<TaskImageRef> refsOf(List<GenerationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tasks.stream().map(GenerationTask::getId).toList();
        return refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .in(TaskImageRef::getTaskId, ids)
                .orderByAsc(TaskImageRef::getSortIndex)
                .orderByAsc(TaskImageRef::getId));
    }

    private List<TaskNode> buildNodes(List<GenerationTask> tasks, List<TaskImageRef> refs) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TaskImageRef>> refsByTask = refs.stream()
                .collect(Collectors.groupingBy(TaskImageRef::getTaskId));
        Set<Long> imageIds = refs.stream().map(TaskImageRef::getImageId).collect(Collectors.toSet());
        Map<Long, CanvasImage> imageMap = new HashMap<>();
        if (!imageIds.isEmpty()) {
            for (CanvasImage image : imageMapper.selectBatchIds(imageIds)) {
                imageMap.put(image.getId(), image);
            }
        }
        return tasks.stream().map(task -> {
            List<TaskImageRef> taskRefs = refsByTask.getOrDefault(task.getId(), List.of());
            List<Long> ids = new ArrayList<>(taskRefs.size());
            List<String> urls = new ArrayList<>(taskRefs.size());
            for (TaskImageRef ref : taskRefs) {
                CanvasImage image = imageMap.get(ref.getImageId());
                if (image == null) {
                    continue;
                }
                ids.add(image.getId());
                urls.add(assetService.accessUrl(image.getUrl()));
            }
            return new TaskNode(
                    task.getId(),
                    task.getWorkspaceId(),
                    normalizeGroup(task.getGroupId()),
                    task.getShotIndex(),
                    task.getTitle(),
                    task.getPrompt(),
                    task.getModel(),
                    modelCatalog.taskKey(task.getProvider(), task.getModel()),
                    task.getRatio(),
                    task.getDuration(),
                    task.getResolution(),
                    task.getProvider(),
                    task.getProviderTaskId(),
                    task.getStatus(),
                    task.getProgress(),
                    assetService.accessUrl(task.getVideoUrl()),
                    assetService.accessUrl(task.getCoverUrl()),
                    task.getErrorMsg(),
                    task.getSubmittedAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    ids,
                    urls
            );
        }).toList();
    }

    private ImageNode toImageNode(CanvasImage image) {
        return new ImageNode(image.getId(), image.getName(), assetService.accessUrl(image.getUrl()),
                image.getX(), image.getY(), image.getWidth(), image.getHeight(),
                image.getPrompt(), image.getStatus(), image.getErrorMsg(), image.getSource());
    }

    private EdgeView toEdgeView(TaskImageRef ref) {
        return new EdgeView(ref.getId(), ref.getTaskId(), ref.getImageId(), ref.getSortIndex());
    }

    private GenerationTask requireTask(Long taskId) {
        GenerationTask task = taskId == null ? null : taskMapper.selectById(taskId);
        if (task == null) {
            throw new NotFoundException("镜头不存在：" + taskId);
        }
        return task;
    }

    private CanvasImage requireImage(Long workspaceId, Long imageId) {
        CanvasImage image = imageId == null ? null : imageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getWorkspaceId(), workspaceId)) {
            throw new NotFoundException("图片不存在：" + imageId);
        }
        return image;
    }

    private Canvas requireCanvas(Long workspaceId) {
        Canvas canvas = canvasMapper.selectOne(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, workspaceId)
                .last("LIMIT 1"));
        if (canvas != null) {
            return canvas;
        }
        if (workspaceMapper.selectById(workspaceId) == null) {
            throw new NotFoundException("画布不存在：" + workspaceId);
        }
        Canvas created = new Canvas();
        created.setWorkspaceId(workspaceId);
        created.setVersion(0);
        created.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(created);
        return created;
    }

    private JsonNode parseSnapshot(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("画布快照解析失败，忽略：{}", e.getMessage());
            return null;
        }
    }

    private GenerationTask findByIdem(String idem) {
        return taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getIdemKey, idem)
                .last("LIMIT 1"));
    }

    private int nextShotIndex(Long workspaceId, String groupId) {
        List<GenerationTask> group = taskMapper.selectList(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getWorkspaceId, workspaceId)
                .eq(GenerationTask::getGroupId, groupId));
        return group.stream()
                .map(GenerationTask::getShotIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private int nextSortIndex(Long taskId) {
        List<TaskImageRef> refs = refMapper.selectList(Wrappers.<TaskImageRef>lambdaQuery()
                .eq(TaskImageRef::getTaskId, taskId));
        return refs.stream()
                .map(TaskImageRef::getSortIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private String normalizeGroup(String groupId) {
        return groupId == null || groupId.isBlank() ? DEFAULT_GROUP : groupId.trim();
    }

    /** 比例按模型能力白名单校验，非法值回退模型默认或首个支持项 */
    private String normalizeRatio(String ratio, ModelCatalog.ResolvedModel model) {
        List<String> allowed = model.ratios().isEmpty()
                ? List.of("16:9", "9:16", "1:1", "4:3", "3:4", "21:9") : model.ratios();
        String value = ratio == null ? "" : ratio.trim();
        if (allowed.contains(value)) {
            return value;
        }
        String fallback = model.defaultRatio();
        if (fallback != null && allowed.contains(fallback)) {
            return fallback;
        }
        return allowed.get(0);
    }

    /** 时长限制在模型支持区间内，未配置时沿用 5 秒 */
    private Integer normalizeDuration(Integer duration, ModelCatalog.ResolvedModel model) {
        List<Integer> allowed = model.durations();
        if (allowed.isEmpty()) {
            return duration == null || duration < 1
                    ? (model.defaultDuration() == null ? 5 : model.defaultDuration()) : duration;
        }
        int min = allowed.stream().min(Integer::compareTo).orElse(1);
        int max = allowed.stream().max(Integer::compareTo).orElse(min);
        int value = duration == null || duration < 1
                ? (model.defaultDuration() == null ? min : model.defaultDuration()) : duration;
        if (allowed.contains(value)) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 每个模型支持的清晰度不同，取不到时回退 720P */
    private String normalizeResolution(String resolution, ModelCatalog.ResolvedModel model) {
        List<String> allowed = model.resolutions().isEmpty()
                ? List.of("480P", "720P") : model.resolutions();
        if (resolution != null && !resolution.isBlank()) {
            String value = resolution.trim().toUpperCase();
            if (allowed.contains(value)) {
                return value;
            }
        }
        return allowed.contains("720P") ? "720P" : allowed.get(0);
    }

    private String defaultTitle(String title, int shotIndex) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return "镜头 " + shotIndex;
    }

    private String defaultImageName(String name, long index) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "图片 " + index;
    }

    private String idemKey(Long workspaceId, String groupId, Integer shotIndex, String prompt) {
        String raw = workspaceId + "|" + groupId + "|" + shotIndex + "|" + (prompt == null ? "" : prompt.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成幂等键失败", e);
        }
    }
}
