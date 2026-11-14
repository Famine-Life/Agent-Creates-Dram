package com.aigc.video.storage;

/**
 * 产物存储抽象：成片 / 参考图统一走这里
 *
 * @param url       前端可直接访问的地址
 * @param objectKey 存储层对象键（本地存储即文件名）
 */
public record StoredAsset(String url, String objectKey) {
}
