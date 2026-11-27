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

    private String defaultPlan = "FREE";

    private Map<String, Integer> videoCosts = new HashMap<>();

    private Map<String, Integer> imageCosts = new HashMap<>();

    private int textCost = 1;

    private int fallbackVideoCost = 50;

    private int fallbackImageCost = 8;
}
