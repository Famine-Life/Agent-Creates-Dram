package com.aigc.video.task;

import com.aigc.video.ai.AiService;
import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.api.dto.CanvasNodeRequest;
import com.aigc.video.api.dto.CanvasNodeView;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.common.BadRequestException;
import com.aigc.video.common.NotFoundException;
import com.aigc.video.domain.Canvas;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.CanvasNode;
import com.aigc.video.domain.Workspace;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.CanvasMapper;
import com.aigc.video.mapper.CanvasNodeMapper;
import com.aigc.video.mapper.WorkspaceMapper;
import com.aigc.video.realtime.CanvasEvent;
import com.aigc.video.realtime.CanvasSocketRegistry;
import com.aigc.video.storage.AssetService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 文本 / 脚本节点与 AI 图片生成 */
/**
 * 处理画布与生成任务的业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanvasNodeService {

    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private final CanvasNodeMapper nodeMapper;
    private final CanvasImageMapper imageMapper;
    private final CanvasMapper canvasMapper;
    private final WorkspaceMapper workspaceMapper;
    private final CanvasSocketRegistry socketRegistry;
    private final AiService aiService;
    private final ModelCatalog modelCatalog;
    private final ObjectMapper objectMapper;
    private final AssetService assetService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "aigc-ai-worker");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------- 文本 / 脚本节点

    public List<CanvasNodeView> list(Long workspaceId) {
        return nodeMapper.selectList(Wrappers.<CanvasNode>lambdaQuery()
                        .eq(CanvasNode::getWorkspaceId, workspaceId)
                        .orderByAsc(CanvasNode::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public CanvasNodeView createNode(Long workspaceId, CanvasNodeRequest request) {
        String type = request == null ? null : request.type();
        if (!CanvasNode.TYPE_TEXT.equals(type) && !CanvasNode.TYPE_SCRIPT.equals(type)) {
            throw new BadRequestException("不支持的节点类型：" + type);
        }
        requireCanvasId(workspaceId);
        LocalDateTime now = LocalDateTime.now();
        CanvasNode node = new CanvasNode();
        node.setWorkspaceId(workspaceId);
        node.setNodeType(type);
        node.setTitle(defaultTitle(type, request.title()));
        node.setPrompt(request.prompt() == null ? "" : request.prompt().trim());
        node.setContent(request.content());
        node.setStatus(STATUS_IDLE);
        node.setX(request.x());
        node.setY(request.y());
        node.setWidth(request.width());
        node.setHeight(request.height());
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        nodeMapper.insert(node);

        CanvasNodeView view = toView(node);
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("canvas_node.created", view));
        return view;
    }

    @Transactional
    public CanvasNodeView updateNode(Long workspaceId, Long nodeId, CanvasNodeRequest request) {
        CanvasNode node = requireNode(workspaceId, nodeId);
        LambdaUpdateWrapper<CanvasNode> update = Wrappers.<CanvasNode>lambdaUpdate()
                .eq(CanvasNode::getId, node.getId());
        if (request.title() != null) {
            update.set(CanvasNode::getTitle, request.title().trim());
        }
        if (request.prompt() != null) {
            update.set(CanvasNode::getPrompt, request.prompt().trim());
        }
        if (request.content() != null) {
            update.set(CanvasNode::getContent, request.content());
        }
        if (request.x() != null) {
            update.set(CanvasNode::getX, request.x());
        }
        if (request.y() != null) {
            update.set(CanvasNode::getY, request.y());
        }
        if (request.width() != null) {
            update.set(CanvasNode::getWidth, request.width());
        }
        if (request.height() != null) {
            update.set(CanvasNode::getHeight, request.height());
        }
        update.set(CanvasNode::getUpdatedAt, LocalDateTime.now());
        nodeMapper.update(null, update);

        CanvasNodeView view = toView(requireNode(workspaceId, nodeId));
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("canvas_node.updated", view));
        return view;
    }

    @Transactional
    public void deleteNode(Long workspaceId, Long nodeId) {
        CanvasNode node = requireNode(workspaceId, nodeId);
        nodeMapper.deleteById(node.getId());
        socketRegistry.broadcast(workspaceId,
                CanvasEvent.of("canvas_node.deleted", java.util.Map.of("nodeId", nodeId)));
    }

    /** 让 AI 根据剧情描述生成分镜脚本，结果以 JSON 数组写入 content */
    public CanvasNodeView generateScript(Long workspaceId, Long nodeId, String prompt, String model) {
        CanvasNode node = requireNode(workspaceId, nodeId);
        String text = prompt == null || prompt.isBlank() ? node.getPrompt() : prompt.trim();
        if (text == null || text.isBlank()) {
            throw new BadRequestException("请先描述剧情片段或故事");
        }
        // 先解析模型，非法模型直接返回 400，不进入异步线程
        ModelCatalog.ResolvedModel resolved = modelCatalog.resolve(ModelCatalog.Capability.TEXT, model);
        updateGenerationState(nodeId, STATUS_RUNNING, text, null, null);
        broadcastNode(workspaceId, nodeId);

        CompletableFuture.runAsync(() -> {
            try {
                AiService.ScriptResult result = aiService.generateScript(text, resolved.key());
                String content = objectMapper.writeValueAsString(result.shots());
                updateGenerationState(nodeId, STATUS_SUCCEEDED, text, content, null);
                nodeMapper.update(null, Wrappers.<CanvasNode>lambdaUpdate()
                        .eq(CanvasNode::getId, nodeId)
                        .set(CanvasNode::getTitle, result.title())
                        .set(CanvasNode::getUpdatedAt, LocalDateTime.now()));
                log.info("[AI] 分镜脚本生成完成 nodeId={} shots={}", nodeId, result.shots().size());
            } catch (Exception e) {
                log.warn("[AI] 分镜脚本生成失败 nodeId={}：{}", nodeId, e.getMessage());
                updateGenerationState(nodeId, STATUS_FAILED, text, null, rootMessage(e));
            }
            broadcastNode(workspaceId, nodeId);
        }, executor);

        return toView(requireNode(workspaceId, nodeId));
    }

    // ---------------------------------------------------------------- AI 图片节点

    @Transactional
    public ImageNode createAiImage(Long workspaceId, Double x, Double y, String prompt) {
        Long canvasId = requireCanvasId(workspaceId);
        long count = imageMapper.selectCount(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getWorkspaceId, workspaceId));
        CanvasImage image = new CanvasImage();
        image.setWorkspaceId(workspaceId);
        image.setCanvasId(canvasId);
        image.setName("AI 图片 " + (count + 1));
        // url 非空约束：生成完成前先用空串占位，前端按 status 展示加载态
        image.setUrl("");
        image.setPrompt(prompt == null ? "" : prompt.trim());
        image.setStatus(STATUS_IDLE);
        image.setSource(ImageNode.SOURCE_AI);
        image.setX(x);
        image.setY(y);
        image.setWidth(260d);
        image.setHeight(260d);
        image.setCreatedAt(LocalDateTime.now());
        imageMapper.insert(image);

        ImageNode view = toImageNode(image);
        socketRegistry.broadcast(workspaceId, CanvasEvent.of("image.created", view));
        return view;
    }

    /** 触发 AI 图片生成，立即返回 RUNNING 状态，完成后通过 WebSocket 推送结果 */
    public ImageNode generateImage(Long workspaceId, Long imageId, String prompt, String model,
                                   String referenceUrl, String ratio, String resolution, Integer count) {
        CanvasImage image = requireImage(workspaceId, imageId);
        String text = prompt == null || prompt.isBlank() ? image.getPrompt() : prompt.trim();
        if (text == null || text.isBlank()) {
            throw new BadRequestException("请先填写图片提示词");
        }
        ModelCatalog.ResolvedModel resolved = modelCatalog.resolve(ModelCatalog.Capability.IMAGE, model);
        int quantity = count == null ? 1 : Math.max(1, Math.min(9, count));
        String size = imageSize(ratio, resolution);
        imageMapper.update(null, Wrappers.<CanvasImage>lambdaUpdate()
                .eq(CanvasImage::getId, imageId)
                .set(CanvasImage::getPrompt, text)
                .set(CanvasImage::getStatus, STATUS_RUNNING)
                .set(CanvasImage::getErrorMsg, null)
                .set(CanvasImage::getSource, ImageNode.SOURCE_AI)
                .set(CanvasImage::getProvider, resolved.providerKey()));
        broadcastImage(workspaceId, imageId);

        CompletableFuture.runAsync(() -> {
            for (int index = 0; index < quantity; index++) {
                try {
                    String url = aiService.generateImage(text, resolved.key(), referenceUrl, size);
                    if (index == 0) {
                        imageMapper.update(null, Wrappers.<CanvasImage>lambdaUpdate()
                                .eq(CanvasImage::getId, imageId)
                                .set(CanvasImage::getUrl, url)
                                .set(CanvasImage::getStatus, STATUS_SUCCEEDED)
                                .set(CanvasImage::getErrorMsg, null));
                        broadcastImage(workspaceId, imageId);
                    } else {
                        createExtraImage(workspaceId, image, text, url, resolved.providerKey(), index);
                    }
                    log.info("[AI] 图片生成完成 imageId={} index={} url={}", imageId, index + 1, url);
                } catch (Exception e) {
                    log.warn("[AI] 图片生成失败 imageId={} index={}：{}", imageId, index + 1, e.getMessage());
                    if (index == 0) {
                        imageMapper.update(null, Wrappers.<CanvasImage>lambdaUpdate()
                                .eq(CanvasImage::getId, imageId)
                                .set(CanvasImage::getStatus, STATUS_FAILED)
                                .set(CanvasImage::getErrorMsg, shorten(rootMessage(e))));
                        broadcastImage(workspaceId, imageId);
                        return;
                    }
                }
            }
        }, executor);

        return toImageNode(requireImage(workspaceId, imageId));
    }

    /** 多张生成时，把第 2 张及之后的图片作为新节点放到原图右侧 */
    private void createExtraImage(Long workspaceId, CanvasImage origin, String prompt, String url,
                                  String provider, int index) {
        CanvasImage extra = new CanvasImage();
        extra.setWorkspaceId(workspaceId);
        extra.setCanvasId(origin.getCanvasId());
        extra.setName((origin.getName() == null ? "AI 图片" : origin.getName()) + " " + (index + 1));
        extra.setUrl(url);
        extra.setPrompt(prompt);
        extra.setStatus(STATUS_SUCCEEDED);
        extra.setProvider(provider);
        extra.setSource(ImageNode.SOURCE_AI);
        extra.setX(origin.getX() == null ? 320.0 + index * 340 : origin.getX() + index * 340);
        extra.setY(origin.getY() == null ? 160.0 : origin.getY());
        extra.setCreatedAt(LocalDateTime.now());
        imageMapper.insert(extra);
        broadcastImage(workspaceId, extra.getId());
    }

    /** 按「比例 + 清晰度」换算成平台可接受的像素尺寸（1K 以 1280×720 为基准） */
    private static String imageSize(String ratio, String resolution) {
        double scale = switch (resolution == null ? "" : resolution) {
            case "2K" -> 2.0;
            case "1.5K" -> 1.5;
            default -> 1.0;
        };
        double rw = 16;
        double rh = 9;
        if (ratio != null && ratio.contains(":")) {
            String[] parts = ratio.split(":");
            try {
                rw = Double.parseDouble(parts[0].trim());
                rh = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException ignored) {
                rw = 16;
                rh = 9;
            }
        }
        if (rw <= 0 || rh <= 0) {
            rw = 16;
            rh = 9;
        }
        double area = 1280.0 * 720.0 * scale * scale;
        double width = Math.sqrt(area * rw / rh);
        double height = width * rh / rw;
        return roundTo8(width) + "x" + roundTo8(height);
    }

    private static long roundTo8(double value) {
        return Math.max(64, Math.round(value / 8) * 8);
    }

    // ---------------------------------------------------------------- 内部实现

    private void updateGenerationState(Long nodeId, String status, String prompt, String content, String error) {
        LambdaUpdateWrapper<CanvasNode> update = Wrappers.<CanvasNode>lambdaUpdate()
                .eq(CanvasNode::getId, nodeId)
                .set(CanvasNode::getStatus, status)
                .set(CanvasNode::getPrompt, prompt)
                .set(CanvasNode::getErrorMsg, error)
                .set(CanvasNode::getUpdatedAt, LocalDateTime.now());
        if (content != null) {
            update.set(CanvasNode::getContent, content);
        }
        nodeMapper.update(null, update);
    }

    private void broadcastNode(Long workspaceId, Long nodeId) {
        try {
            socketRegistry.broadcast(workspaceId,
                    CanvasEvent.of("canvas_node.updated", toView(requireNode(workspaceId, nodeId))));
        } catch (Exception e) {
            log.warn("广播画布节点失败 nodeId={}：{}", nodeId, e.getMessage());
        }
    }

    private void broadcastImage(Long workspaceId, Long imageId) {
        try {
            socketRegistry.broadcast(workspaceId,
                    CanvasEvent.of("image.updated", toImageNode(requireImage(workspaceId, imageId))));
        } catch (Exception e) {
            log.warn("广播图片节点失败 imageId={}：{}", imageId, e.getMessage());
        }
    }

    private CanvasNodeView toView(CanvasNode node) {
        return new CanvasNodeView(
                node.getId(),
                node.getWorkspaceId(),
                node.getNodeType(),
                node.getTitle(),
                node.getPrompt(),
                node.getContent(),
                node.getStatus(),
                node.getErrorMsg(),
                node.getX(),
                node.getY(),
                node.getWidth(),
                node.getHeight());
    }

    private ImageNode toImageNode(CanvasImage image) {
        return new ImageNode(image.getId(), image.getName(), assetService.accessUrl(image.getUrl()),
                image.getX(), image.getY(), image.getWidth(), image.getHeight(),
                image.getPrompt(), image.getStatus(), image.getErrorMsg(), image.getSource());
    }

    private CanvasNode requireNode(Long workspaceId, Long nodeId) {
        CanvasNode node = nodeId == null ? null : nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getWorkspaceId(), workspaceId)) {
            throw new NotFoundException("节点不存在：" + nodeId);
        }
        return node;
    }

    private CanvasImage requireImage(Long workspaceId, Long imageId) {
        CanvasImage image = imageId == null ? null : imageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getWorkspaceId(), workspaceId)) {
            throw new NotFoundException("图片不存在：" + imageId);
        }
        return image;
    }

    private Long requireCanvasId(Long workspaceId) {
        Canvas canvas = canvasMapper.selectOne(Wrappers.<Canvas>lambdaQuery()
                .eq(Canvas::getWorkspaceId, workspaceId)
                .last("LIMIT 1"));
        if (canvas != null) {
            return canvas.getId();
        }
        if (workspaceMapper.selectById(workspaceId) == null) {
            Workspace workspace = new Workspace();
            workspace.setId(workspaceId);
            workspace.setName("工作空间 " + workspaceId);
            workspace.setCreatedAt(LocalDateTime.now());
            workspaceMapper.insert(workspace);
        }
        Canvas created = new Canvas();
        created.setWorkspaceId(workspaceId);
        created.setVersion(0);
        created.setUpdatedAt(LocalDateTime.now());
        canvasMapper.insert(created);
        return created.getId();
    }

    private String defaultTitle(String type, String title) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return CanvasNode.TYPE_SCRIPT.equals(type) ? "脚本生成器" : "文本";
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    private String shorten(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
