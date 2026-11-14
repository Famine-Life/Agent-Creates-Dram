package com.aigc.video.storage;

/** 存储实现：本地磁盘 / MinIO / 腾讯云 COS / 阿里云 OSS，由 aigc.storage.type 决定启用哪一个 */
public interface AssetStorage {

    String type();

    StoredAsset store(byte[] bytes, String fileName, String contentType);

    /** 判断地址是否已经指向当前存储，避免把自家产物再次下载转存 */
    default boolean owns(String url) {
        return false;
    }

    /** 返回本次响应使用的访问地址；私有对象存储实现可生成短期签名 URL。 */
    default String accessUrl(String url) {
        return url;
    }
}
