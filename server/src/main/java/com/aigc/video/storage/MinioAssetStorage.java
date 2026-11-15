package com.aigc.video.storage;

import com.aigc.video.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/** MinIO 对象存储，需要本地起 MinIO（见 docker-compose.yml 的 minio 服务） */
/**
 * 管理生成资源与文件存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.storage", name = "type", havingValue = "minio")
public class MinioAssetStorage implements AssetStorage {

    private final StorageProperties properties;

    private volatile MinioClient client;

    @Override
    public String type() {
        return "minio";
    }

    @Override
    public StoredAsset store(byte[] bytes, String fileName, String contentType) {
        StorageProperties.Minio cfg = properties.getMinio();
        try {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(cfg.getBucket())
                    .object(fileName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传 MinIO 失败：" + fileName, e);
        }
        String base = cfg.getEndpoint();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new StoredAsset(base + "/" + cfg.getBucket() + "/" + fileName, fileName);
    }

    @Override
    public boolean owns(String url) {
        return url != null && url.startsWith(publicBase() + "/");
    }

    @Override
    public String accessUrl(String url) {
        if (!owns(url)) {
            return url;
        }
        StorageProperties.Minio cfg = properties.getMinio();
        String objectKey = url.substring((publicBase() + "/").length());
        int query = objectKey.indexOf('?');
        if (query >= 0) {
            objectKey = objectKey.substring(0, query);
        }
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(cfg.getBucket())
                    .object(objectKey)
                    .expiry(properties.getSignedUrlTtlSeconds())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成 MinIO 签名地址失败：" + objectKey, e);
        }
    }

    private String publicBase() {
        StorageProperties.Minio cfg = properties.getMinio();
        String base = cfg.getEndpoint();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + cfg.getBucket();
    }

    private void ensureBucket() throws Exception {
        StorageProperties.Minio cfg = properties.getMinio();
        if (!client().bucketExists(BucketExistsArgs.builder().bucket(cfg.getBucket()).build())) {
            client().makeBucket(MakeBucketArgs.builder().bucket(cfg.getBucket()).build());
            log.info("已创建私有 MinIO 桶 {}", cfg.getBucket());
        }
    }

    private MinioClient client() {
        MinioClient local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Minio cfg = properties.getMinio();
                    client = MinioClient.builder()
                            .endpoint(cfg.getEndpoint())
                            .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                            .build();
                }
                local = client;
            }
        }
        return local;
    }
}
