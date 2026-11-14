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
        String prefix = properties.getPublicPrefix();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        return new StoredAsset(prefix + "/" + fileName, fileName);
    }
}
