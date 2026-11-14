package com.aigc.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 脚本 / 图片生成配置；未配置 api-key 时自动走 mock，便于本地跑通 */
@Data
@Component
@ConfigurationProperties(prefix = "aigc.ai")
public class AiProperties {

    private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";

    private String apiKey = "";

    /** 分镜脚本模型（火山方舟 · 豆包） */
    private String scriptModel = "doubao-seed-1-6-250615";

    /** 图片生成模型（火山方舟 · 即梦 Seedream） */
    private String imageModel = "doubao-seedream-3-0-t2i-250415";

    public boolean mockEnabled() {
        return apiKey == null || apiKey.isBlank();
    }
}
