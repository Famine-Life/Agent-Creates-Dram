package com.aigc.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aigc.provider")
public class ProviderProperties {

    /** 默认供应商：mock / seedance */
    private String defaultName = "mock";

    private Mock mock = new Mock();

    private Seedance seedance = new Seedance();

    @Data
    public static class Mock {
        private String videoUrl = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";
        /** Mock 生成耗时（秒），用于演示进度 */
        private int seconds = 8;
    }

    @Data
    public static class Seedance {
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
        private String apiKey = "";
        private String model = "doubao-seedance-2-0-260128";
        private String ratio = "16:9";
        private Integer duration = 5;
        private Boolean watermark = false;
    }
}
