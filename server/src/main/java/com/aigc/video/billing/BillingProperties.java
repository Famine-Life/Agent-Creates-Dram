package com.aigc.video.billing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 生成计费配置，按 provider/model 定价。 */
@Data
@Component
@ConfigurationProperties(prefix = "aigc.billing")
public class BillingProperties {

    /** 未指定时的默认会员档位 */
    private String defaultPlan = "FREE";

    /** 按 provider/model 配置的视频基础单价（按 5 秒计） */
    private Map<String, Integer> videoCosts = new HashMap<>();

    /** 按 provider/model 配置的单张图片单价 */
    private Map<String, Integer> imageCosts = new HashMap<>();

    /** 文本生成每次消耗的积分 */
    private int textCost = 1;

    /** 视频模型未配置时的兜底单价 */
    private int fallbackVideoCost = 50;

    /** 图片模型未配置时的兜底单价 */
    private int fallbackImageCost = 8;
}
