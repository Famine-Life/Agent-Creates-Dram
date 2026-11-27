package com.aigc.video.api;

import com.aigc.video.api.dto.ImageNode;
import com.aigc.video.api.dto.ImageRequest;
import com.aigc.video.api.dto.UploadResult;
import com.aigc.video.storage.AssetService;
import com.aigc.video.storage.StoredAsset;
import com.aigc.video.task.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 提供应用的 HTTP 接口。
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final AssetService assetService;
    private final TaskService taskService;
    private final com.aigc.video.workspace.WorkspaceAccess workspaceAccess;

    /**
     * 上传参考图并直接在画布上创建图片节点。
     * 返回的 id 可直接用于 POST /shots 的 imageIds。
     */
    @PostMapping
    public UploadResult upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long workspaceId,
                               @RequestParam(required = false) Double x,
                               @RequestParam(required = false) Double y) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        Long userId = com.aigc.video.user.UserContext.requireUserId();
        Long targetWorkspace = workspaceId == null
                ? workspaceAccess.firstOwnedId() : workspaceId;
        StoredAsset stored = assetService.storeImage(userId,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        ImageNode node = taskService.createImage(targetWorkspace,
                new ImageRequest(file.getOriginalFilename(), stored.url(), stored.objectKey(),
                        x, y, null, null, null, null, ImageNode.SOURCE_UPLOAD, null));
        return new UploadResult(node.id(), node.name(), node.url());
    }
}
