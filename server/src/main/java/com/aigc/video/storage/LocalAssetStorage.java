package com.aigc.video.storage;

import com.aigc.video.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/** 本地磁盘存储，配合 WebConfig 的 /files/** 静态映射对外提供访问 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalAssetStorage implements AssetStorage {

    private final StorageProperties properties;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public StoredAsset store(byte[] bytes, String fileName, String contentType) {
        Path root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的文件名：" + fileName);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("写入本地文件失败：" + target, e);
        }
        return new StoredAsset(publicPrefix() + "/" + fileName, fileName);
    }

    @Override
    public String providerImageUrl(String url) {
        String prefix = publicPrefix() + "/";
        if (url == null || !url.startsWith(prefix)) {
            return url;
        }
        Path root = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        Path target = root.resolve(url.substring(prefix.length())).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的参考图地址");
        }
        try {
            String mediaType = imageMediaType(target.getFileName().toString());
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(target));
            return "data:" + mediaType + ";base64," + encoded;
        } catch (IOException e) {
            throw new UncheckedIOException("读取本地参考图失败：" + target, e);
        }
    }

    private String publicPrefix() {
        String prefix = properties.getPublicPrefix();
        return prefix.startsWith("/") ? prefix : "/" + prefix;
    }

    private String imageMediaType(String fileName) {
        String name = fileName.toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png", "webp", "bmp", "gif", "heic", "heif" -> "image/" + ext;
            case "tif", "tiff" -> "image/tiff";
            default -> throw new IllegalArgumentException("不支持的参考图格式：" + fileName);
        };
    }
}
