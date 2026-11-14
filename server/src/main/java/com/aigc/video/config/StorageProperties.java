package com.aigc.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aigc.storage")
public class StorageProperties {

    /** local / minio / cos / oss */
    private String type = "local";

    private String localDir = "./data/files";

    private String publicPrefix = "/files";

    /** MinIO / COS / OSS 私有对象的 GET 预签名地址有效期 */
    private int signedUrlTtlSeconds = 3600;

    private Minio minio = new Minio();

    private Cos cos = new Cos();

    private Oss oss = new Oss();

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "aigc";
    }

    /** 腾讯云 COS */
    @Data
    public static class Cos {
        /** 访问域名，如 https://aigc-1250000000.cos.ap-guangzhou.myqcloud.com */
        private String endpoint;
        private String secretId;
        private String secretKey;
        private String bucket;
        private String region;
        /** 可选：CDN / 自定义域名，配置后优先用于拼接访问地址 */
        private String publicUrl;
    }

    /** 阿里云 OSS */
    @Data
    public static class Oss {
        /** 地域节点，如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        /** 可选：CDN / 自定义域名，配置后优先用于拼接访问地址 */
        private String publicUrl;
    }
}
