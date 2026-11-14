package com.aigc.video.storage;

/**
 * COS / OSS 的访问地址拼接：优先 public-url（CDN/自定义域名），
 * 未配置时按 bucket + endpoint 拼出默认域名。
 */
final class BucketUrl {

    private BucketUrl() {
    }

    /**
     * @param publicUrl     可选的自定义访问域名
     * @param endpoint      服务商地域节点
     * @param bucket        桶名
     * @param propertyPrefix 配置前缀，仅用于报错提示
     */
    static String base(String publicUrl, String endpoint, String bucket, String propertyPrefix) {
        String custom = trim(publicUrl);
        if (!custom.isEmpty()) {
            return custom;
        }
        String host = trim(endpoint);
        if (host.isEmpty()) {
            throw new IllegalStateException("请配置 " + propertyPrefix + ".endpoint 或 public-url");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("缺少配置：" + propertyPrefix + ".bucket");
        }
        String scheme = "https://";
        int schemeIdx = host.indexOf("://");
        if (schemeIdx > 0) {
            scheme = host.substring(0, schemeIdx + 3);
            host = host.substring(schemeIdx + 3);
        }
        // endpoint 已经带上 bucket（virtual-hosted 风格）时直接使用
        if (host.startsWith(bucket + ".")) {
            return scheme + host;
        }
        return scheme + bucket + "." + host;
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
