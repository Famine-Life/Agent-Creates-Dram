package com.aigc.video.provider;

import java.util.List;

/**
 * 提交给视频供应商的生成参数
 *
 * @param prompt    提示词
 * @param title     镜头标题（仅日志用）
 * @param imageUrls 参考图公网地址或 data URI，按 @图片1、@图片2 顺序排列
 */
public record SubmitCommand(
        String prompt,
        String title,
        List<String> imageUrls,
        String ratio,
        Integer duration,
        String resolution
) {

    public SubmitCommand {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
