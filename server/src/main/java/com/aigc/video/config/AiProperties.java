package com.aigc.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多平台 AI 配置：平台（provider）只管地址与密钥，模型（model）声明能力与协议。
 * 模型通过 "provider/model" 复合标识引用，如 ark/doubao-seed-evolving。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigc.ai")
public class AiProperties {

    /** 默认平台（仅用于裸模型 ID 的兜底解析） */
    private String defaultProvider = "ark";

    private Defaults defaults = new Defaults();

    /** 平台配置，key 为平台标识（ark / openai / dashscope / mock …） */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Data
    public static class Defaults {
        /** 默认文本模型：provider/model */
        private String text;
        /** 默认图片模型：provider/model */
        private String image;
        /** 默认视频模型：provider/model */
        private String video;
    }

    @Data
    public static class Provider {
        /** 展示名称，如「火山方舟」 */
        private String label;
        /** 平台 API 根地址 */
        private String baseUrl;
        /** 平台密钥；为空视为未配置，该平台模型不会出现在前端下拉中 */
        private String apiKey;
        /** 各能力的默认协议：text / image / video */
        private Map<String, String> protocols = new LinkedHashMap<>();
        private List<Model> textModels = new ArrayList<>();
        private List<Model> imageModels = new ArrayList<>();
        private List<Model> videoModels = new ArrayList<>();
        /** mock 平台专用：演示成片地址 */
        private String videoUrl;
        /** mock 平台专用：演示出片耗时（秒） */
        private int videoSeconds = 8;
    }

    @Data
    public static class Model {
        private String id;
        private String label;
        /** 可选：覆盖平台在该能力上的默认协议 */
        private String protocol;
        /** 视频模型支持的比例 */
        private List<String> ratios = new ArrayList<>();
        /** 视频模型支持的清晰度 */
        private List<String> resolutions = new ArrayList<>();
        /** 视频模型支持的时长（秒） */
        private List<Integer> durations = new ArrayList<>();
        private String defaultRatio;
        private Integer defaultDuration;
        private Boolean watermark;
    }
}
