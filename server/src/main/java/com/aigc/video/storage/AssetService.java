package com.aigc.video.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.UUID;

/** 上传与转存的统一入口 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final AssetStorage storage;

    /** 同一远端地址只下载一次（mock 演示视频所有任务共用同一个地址） */
    private final java.util.concurrent.ConcurrentHashMap<String, String> transferred = new java.util.concurrent.ConcurrentHashMap<>();

    @PostConstruct
    void logStorage() {
        log.info("资源存储实现：{}", storage.type());
    }

    /** 只在组装响应时签名，数据库始终保存稳定 URL。 */
    public String accessUrl(String url) {
        return storage.accessUrl(url);
    }

    /** 保存前端上传的图片 */
    public StoredAsset storeImage(String originalName, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("上传内容为空");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        String mime = contentType == null ? "" : contentType.toLowerCase();
        if (!mime.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持 jpg / png / webp 图片");
        }
        String ext = extension(resolveName(originalName, mime));
        return storage.store(bytes, "images/" + UUID.randomUUID() + ext, contentType);
    }

    /**
     * 把供应商返回的地址转存到自己的存储；失败时保留原始地址，保证演示链路不中断。
     *
     * @param sourceUrl 远端地址
     * @param folder    videos / images
     * @param fallbackExt 无法推断后缀时使用的后缀，如 .mp4
     */
    public String transfer(String sourceUrl, String folder, String fallbackExt) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        // 本地相对路径或已经落在当前存储里，无需再次转存
        if (sourceUrl.startsWith("/") || storage.owns(sourceUrl)) {
            return sourceUrl;
        }
        String cached = transferred.get(sourceUrl);
        if (cached != null) {
            return cached;
        }
        try {
            byte[] bytes = download(sourceUrl);
            String ext = extension(sourceUrl);
            if (ext.isBlank()) {
                ext = fallbackExt;
            }
            StoredAsset stored = storage.store(bytes, folder + "/" + UUID.randomUUID() + ext, null);
            transferred.put(sourceUrl, stored.url());
            log.info("产物已转存 {} -> {}", sourceUrl, stored.url());
            return stored.url();
        } catch (Exception e) {
            log.warn("产物转存失败，直接使用原始地址 {}（{}）", sourceUrl, e.getMessage());
            return sourceUrl;
        }
    }

    private byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "aigc-video-server/0.1");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    private String resolveName(String originalName, String mime) {
        if (originalName != null && !originalName.isBlank()) {
            return originalName;
        }
        return switch (mime) {
            case "image/png" -> "image.png";
            case "image/webp" -> "image.webp";
            default -> "image.jpg";
        };
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return "";
        }
        String ext = name.substring(idx).toLowerCase();
        if (ext.length() > 6 || !ext.matches("\\.[a-z0-9]+")) {
            return "";
        }
        return ext;
    }
}
