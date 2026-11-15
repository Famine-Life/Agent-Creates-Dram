package com.aigc.video.api;

import com.aigc.video.api.dto.AiImageRequest;
import com.aigc.video.api.dto.CanvasNodeRequest;
import com.aigc.video.api.dto.CanvasNodeView;
import com.aigc.video.api.dto.CanvasState;
import com.aigc.video.api.dto.EdgeRequest;
import com.aigc.video.api.dto.EdgeView;
import com.aigc.video.api.dto.GenerateRequest;
import com.aigc.video.api.dto.ImageGenerateRequest;
import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.ImageRequest;
import com.aigc.video.api.dto.OrderRequest;
import com.aigc.video.api.dto.ShotRequest;
import com.aigc.video.api.dto.SnapshotRequest;
import com.aigc.video.api.dto.StoryboardRequest;
import com.aigc.video.api.dto.TaskNode;
import com.aigc.video.ai.AiService;
import com.aigc.video.domain.PromptRecord;
import com.aigc.video.task.CanvasNodeService;
import com.aigc.video.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/canvas/{workspaceId}")
@RequiredArgsConstructor
public class CanvasController {

    private final TaskService taskService;
    private final CanvasNodeService canvasNodeService;
    private final AiService aiService;

    @PostMapping("/ai/prompt/optimize")
    public Map<String, String> optimizePrompt(@PathVariable Long workspaceId,
                                              @RequestBody Map<String, String> request) {
        String prompt = request == null ? null : request.get("prompt");
        String model = request == null ? null : request.get("model");
        return Map.of("prompt", aiService.optimizePrompt(prompt, model));
    }

    @GetMapping
    public CanvasState state(@PathVariable Long workspaceId) {
        return taskService.getCanvas(workspaceId);
    }

    @GetMapping("/prompt-records")
    public List<PromptRecord> promptRecords(@PathVariable Long workspaceId) {
        return taskService.listPromptRecords(workspaceId);
    }

    @PostMapping("/shots")
    public TaskNode createShot(@PathVariable Long workspaceId, @RequestBody ShotRequest request) {
        return taskService.createShot(workspaceId, request);
    }

    @PostMapping("/storyboards")
    public List<TaskNode> createStoryboard(@PathVariable Long workspaceId,
                                           @RequestBody StoryboardRequest request) {
        return taskService.createStoryboard(workspaceId, request);
    }

    @PutMapping("/snapshot")
    public CanvasState saveSnapshot(@PathVariable Long workspaceId,
                                    @RequestBody SnapshotRequest request) {
        return taskService.saveSnapshot(workspaceId, request.snapshot(), request.baseVersion());
    }

    @PutMapping("/order")
    public CanvasState reorder(@PathVariable Long workspaceId, @RequestBody OrderRequest request) {
        return taskService.reorder(workspaceId, request);
    }

    @PostMapping("/images")
    public ImageNode createImage(@PathVariable Long workspaceId, @RequestBody ImageRequest request) {
        return taskService.createImage(workspaceId, request);
    }

    /** 创建 AI 图片节点（先占位，填写提示词后再生成） */
    @PostMapping("/images/ai")
    public ImageNode createAiImage(@PathVariable Long workspaceId,
                                   @RequestBody AiImageRequest request) {
        return canvasNodeService.createAiImage(workspaceId, request.x(), request.y(), request.prompt());
    }

    /** 触发 AI 图片生成，完成后通过 WebSocket 推送 image.updated */
    @PostMapping("/images/{imageId}/generate")
    public ImageNode generateImage(@PathVariable Long workspaceId,
                                   @PathVariable Long imageId,
                                   @RequestBody(required = false) ImageGenerateRequest request) {
        return canvasNodeService.generateImage(workspaceId, imageId,
                request == null ? null : request.prompt(),
                request == null ? null : request.model(),
                request == null ? null : request.referenceUrl(),
                request == null ? null : request.ratio(),
                request == null ? null : request.resolution(),
                request == null ? null : request.count());
    }

    // ---------------------------------------------------------------- 文本 / 脚本节点

    @PostMapping("/nodes")
    public CanvasNodeView createNode(@PathVariable Long workspaceId,
                                     @RequestBody CanvasNodeRequest request) {
        return canvasNodeService.createNode(workspaceId, request);
    }

    @PutMapping("/nodes/{nodeId}")
    public CanvasNodeView updateNode(@PathVariable Long workspaceId,
                                     @PathVariable Long nodeId,
                                     @RequestBody CanvasNodeRequest request) {
        return canvasNodeService.updateNode(workspaceId, nodeId, request);
    }

    @DeleteMapping("/nodes/{nodeId}")
    public Map<String, Object> deleteNode(@PathVariable Long workspaceId,
                                          @PathVariable Long nodeId) {
        canvasNodeService.deleteNode(workspaceId, nodeId);
        return Map.of("ok", true);
    }

    /** AI 生成分镜脚本 */
    @PostMapping("/nodes/{nodeId}/script")
    public CanvasNodeView generateScript(@PathVariable Long workspaceId,
                                         @PathVariable Long nodeId,
                                         @RequestBody(required = false) GenerateRequest request) {
        return canvasNodeService.generateScript(workspaceId, nodeId,
                request == null ? null : request.prompt(),
                request == null ? null : request.model());
    }

    @PutMapping("/images/{imageId}")
    public ImageNode updateImage(@PathVariable Long workspaceId,
                                 @PathVariable Long imageId,
                                 @RequestBody ImageRequest request) {
        return taskService.updateImage(workspaceId, imageId, request);
    }

    @DeleteMapping("/images/{imageId}")
    public Map<String, Object> deleteImage(@PathVariable Long workspaceId,
                                           @PathVariable Long imageId) {
        taskService.deleteImage(workspaceId, imageId);
        return Map.of("ok", true);
    }

    @PostMapping("/edges")
    public EdgeView createEdge(@PathVariable Long workspaceId, @RequestBody EdgeRequest request) {
        return taskService.createEdge(workspaceId, request);
    }

    @DeleteMapping("/edges/{edgeId}")
    public Map<String, Object> deleteEdge(@PathVariable Long workspaceId,
                                          @PathVariable Long edgeId) {
        taskService.deleteEdge(workspaceId, edgeId);
        return Map.of("ok", true);
    }
}
