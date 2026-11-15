package com.aigc.video.provider;

/**
 * 视频协议适配器的运行时上下文，来自 provider/model 解析结果。
 * 适配器不读取全局配置，同一实例可服务多个平台。
 */
public record VideoContext(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Boolean watermark
) {
}
