package com.aigc.video.provider;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按名称查找供应商，未命中时回退到 mock，保证演示链路始终可用 */
@Component
public class ProviderRegistry {

    private final Map<String, VideoProvider> providers = new HashMap<>();

    public ProviderRegistry(List<VideoProvider> providerList) {
        for (VideoProvider provider : providerList) {
            providers.put(provider.name(), provider);
        }
    }

    public VideoProvider get(String name) {
        VideoProvider provider = name == null ? null : providers.get(name);
        if (provider != null) {
            return provider;
        }
        VideoProvider fallback = providers.get("mock");
        if (fallback == null) {
            throw new IllegalStateException("未注册任何视频供应商");
        }
        return fallback;
    }
}
