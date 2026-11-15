package com.aigc.video.storage;

import com.aigc.video.config.StorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Date;

/** 腾讯云 COS 图床/对象存储，aigc.storage.type=cos 时启用 */
/**
 * 管理生成资源与文件存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigc.storage", name = "type", havingValue = "cos")
public class CosAssetStorage implements AssetStorage {

    private final StorageProperties properties;

    private volatile COSClient client;

    @Override
    public String type() {
        return "cos";
    }

    @Override
    public StoredAsset store(byte[] bytes, String fileName, String contentType) {
        StorageProperties.Cos cfg = properties.getCos();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(contentType == null ? "application/octet-stream" : contentType);
        try {
            PutObjectRequest request = new PutObjectRequest(
                    cfg.getBucket(), fileName, new ByteArrayInputStream(bytes), metadata);
            request.setCannedAcl(CannedAccessControlList.Private);
            client().putObject(request);
        } catch (Exception e) {
            throw new IllegalStateException("上传腾讯云 COS 失败：" + fileName, e);
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
        StorageProperties.Cos cfg = properties.getCos();
        String objectKey = url.substring((baseUrl() + "/").length());
        int query = objectKey.indexOf('?');
        if (query >= 0) {
            objectKey = objectKey.substring(0, query);
        }
        Date expiration = new Date(System.currentTimeMillis() + properties.getSignedUrlTtlSeconds() * 1000L);
        try {
            return client().generatePresignedUrl(cfg.getBucket(), objectKey, expiration).toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成腾讯云 COS 签名地址失败：" + objectKey, e);
        }
    }

    private String baseUrl() {
        StorageProperties.Cos cfg = properties.getCos();
        return BucketUrl.base(cfg.getPublicUrl(), cfg.getEndpoint(), cfg.getBucket(), "aigc.storage.cos");
    }

    private COSClient client() {
        COSClient local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Cos cfg = properties.getCos();
                    require(cfg.getSecretId(), "aigc.storage.cos.secret-id");
                    require(cfg.getSecretKey(), "aigc.storage.cos.secret-key");
                    require(cfg.getBucket(), "aigc.storage.cos.bucket");
                    require(cfg.getRegion(), "aigc.storage.cos.region");
                    COSCredentials credentials = new BasicCOSCredentials(cfg.getSecretId(), cfg.getSecretKey());
                    client = new COSClient(credentials, new ClientConfig(new Region(cfg.getRegion())));
                    log.info("腾讯云 COS 已启用 bucket={} region={}", cfg.getBucket(), cfg.getRegion());
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
