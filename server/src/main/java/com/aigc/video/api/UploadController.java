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

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final AssetService assetService;
    private final TaskService taskService;

    /**
     * 上传参考图并直接在画布上创建图片节点。
     * 返回的 id 可直接用于 POST /shots 的 imageIds。
     */
    @PostMapping
    public UploadResult upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(defaultValue = "1") Long workspaceId,
                               @RequestParam(required = false) Double x,
                               @RequestParam(required = false) Double y) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        StoredAsset stored = assetService.storeImage(
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        ImageNode node = taskService.createImage(workspaceId,
                new ImageRequest(file.getOriginalFilename(), stored.url(), stored.objectKey(),
                        x, y, null, null, null, null, ImageNode.SOURCE_UPLOAD));
        return new UploadResult(node.id(), node.name(), node.url());
    }
}
