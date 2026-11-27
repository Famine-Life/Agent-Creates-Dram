package com.aigc.video.api;

import com.aigc.video.common.NotFoundException;
import com.aigc.video.config.StorageProperties;
import com.aigc.video.domain.CanvasImage;
import com.aigc.video.domain.GenerationTask;
import com.aigc.video.mapper.CanvasImageMapper;
import com.aigc.video.mapper.GenerationTaskMapper;
import com.aigc.video.user.UserContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** 本地存储资源访问：必须登录，并校验资源归属。 */
@RestController
@RequiredArgsConstructor
public class FileAccessController {

    private final StorageProperties storageProperties;
    private final CanvasImageMapper imageMapper;
    private final GenerationTaskMapper taskMapper;

    @GetMapping("/files/**")
    public ResponseEntity<Resource> file(HttpServletRequest request) {
        Long userId = UserContext.requireUserId();
        String uri = request.getRequestURI();
        String prefix = publicPrefix();
        if (!uri.startsWith(prefix + "/")) {
            throw new NotFoundException("资源不存在");
        }
        String relative = URLDecoder.decode(uri.substring(prefix.length() + 1), StandardCharsets.UTF_8);
        int slash = relative.indexOf('/');
        String first = slash > 0 ? relative.substring(0, slash) : "";
        if (first.matches("\\d+")) {
            boolean owner = Long.valueOf(first).equals(userId);
            if (!owner && !UserContext.isAdmin()) {
                throw new NotFoundException("资源不存在");
            }
        } else if (!UserContext.isAdmin() && !legacyOwned(userId, uri)) {
            // 单用户升级前的旧路径没有用户前缀，按数据库记录校验归属
            throw new NotFoundException("资源不存在");
        }

        Path root = Paths.get(storageProperties.getLocalDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new NotFoundException("资源不存在");
        }
        return ResponseEntity.ok()
                .contentType(mediaType(target.getFileName().toString()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(new FileSystemResource(target));
    }

    private boolean legacyOwned(Long userId, String url) {
        Long images = imageMapper.selectCount(Wrappers.<CanvasImage>lambdaQuery()
                .eq(CanvasImage::getUserId, userId)
                .eq(CanvasImage::getUrl, url));
        if (images != null && images > 0) {
            return true;
        }
        Long tasks = taskMapper.selectCount(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getUserId, userId)
                .and(w -> w.eq(GenerationTask::getVideoUrl, url)
                        .or().eq(GenerationTask::getCoverUrl, url)));
        return tasks != null && tasks > 0;
    }

    private String publicPrefix() {
        String prefix = storageProperties.getPublicPrefix();
        if (prefix == null || prefix.isBlank()) {
            return "/files";
        }
        return prefix.startsWith("/") ? prefix : "/" + prefix;
    }

    private MediaType mediaType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        if (lower.endsWith(".webm")) {
            return MediaType.parseMediaType("video/webm");
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
