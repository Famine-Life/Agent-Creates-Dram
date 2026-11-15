package com.aigc.video.provider;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按协议名称查找视频适配器；不支持时明确报错，避免静默回退到 mock */
/**
 * 封装外部生成平台的调用协议。
 */
@Component
public class ProviderRegistry {

    private final Map<String, VideoProvider> providers = new HashMap<>();

    public ProviderRegistry(List<VideoProvider> providerList) {
        for (VideoProvider provider : providerList) {
            providers.put(provider.name(), provider);
        }
    }

    public VideoProvider get(String protocol) {
        VideoProvider provider = protocol == null ? null : providers.get(protocol);
        if (provider == null) {
            throw new IllegalArgumentException("不支持的视频协议：" + protocol);
        }
        return provider;
    }

    public boolean supports(String protocol) {
        return protocol != null && providers.containsKey(protocol);
    }
}
