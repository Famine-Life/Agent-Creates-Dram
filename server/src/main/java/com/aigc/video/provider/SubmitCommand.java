package com.aigc.video.provider;

import java.util.List;

/**
 * 提交给视频协议适配器的生成参数
 *
 * @param prompt    提示词
 * @param title     镜头标题（仅日志用）
 * @param imageUrls 参考图公网地址或 data URI，按 @图片1、@图片2 顺序排列
 * @param context   平台地址、密钥与真实模型 ID
 */
public record SubmitCommand(
        String prompt,
        String title,
        List<String> imageUrls,
        String ratio,
        Integer duration,
        String resolution,
        VideoContext context
) {

    public SubmitCommand {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
