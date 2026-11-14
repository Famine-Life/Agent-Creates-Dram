package com.aigc.video.storage;

import com.aigc.video.config.StorageProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Date;

/** 阿里云 OSS 图床/对象存储，aigc.storage.type=oss 时启用 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.storage", name = "type", havingValue = "oss")
public class OssAssetStorage implements AssetStorage {

    private final StorageProperties properties;

    private volatile OSS client;

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public StoredAsset store(byte[] bytes, String fileName, String contentType) {
        StorageProperties.Oss cfg = properties.getOss();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType == null ? "application/octet-stream" : contentType);
        metadata.setObjectAcl(CannedAccessControlList.Private);
        try {
            client().putObject(cfg.getBucket(), fileName, new ByteArrayInputStream(bytes), metadata);
        } catch (Exception e) {
            throw new IllegalStateException("上传阿里云 OSS 失败：" + fileName, e);
        }
        return new StoredAsset(baseUrl() + "/" + fileName, fileName);
    }

    @Override
    public boolean owns(String url) {
        return url != null && url.startsWith(baseUrl() + "/");
    }

    @Override
    public String accessUrl(String url) {
        if (!owns(url)) {
            return url;
        }
        StorageProperties.Oss cfg = properties.getOss();
        String objectKey = url.substring((baseUrl() + "/").length());
        int query = objectKey.indexOf('?');
        if (query >= 0) {
            objectKey = objectKey.substring(0, query);
        }
        Date expiration = new Date(System.currentTimeMillis() + properties.getSignedUrlTtlSeconds() * 1000L);
        try {
            return client().generatePresignedUrl(cfg.getBucket(), objectKey, expiration).toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成阿里云 OSS 签名地址失败：" + objectKey, e);
        }
    }

    private String baseUrl() {
        StorageProperties.Oss cfg = properties.getOss();
        return BucketUrl.base(cfg.getPublicUrl(), cfg.getEndpoint(), cfg.getBucket(), "aigc.storage.oss");
    }

    private OSS client() {
        OSS local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Oss cfg = properties.getOss();
                    require(cfg.getEndpoint(), "aigc.storage.oss.endpoint");
                    require(cfg.getAccessKeyId(), "aigc.storage.oss.access-key-id");
                    require(cfg.getAccessKeySecret(), "aigc.storage.oss.access-key-secret");
                    require(cfg.getBucket(), "aigc.storage.oss.bucket");
                    client = new OSSClientBuilder()
                            .build(cfg.getEndpoint(), cfg.getAccessKeyId(), cfg.getAccessKeySecret());
                    log.info("阿里云 OSS 已启用 bucket={} endpoint={}", cfg.getBucket(), cfg.getEndpoint());
                }
                local = client;
            }
        }
        return local;
    }

    private static void require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少配置：" + key);
        }
    }
}
