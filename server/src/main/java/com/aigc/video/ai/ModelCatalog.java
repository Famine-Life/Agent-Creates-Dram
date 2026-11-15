package com.aigc.video.ai;

import com.aigc.video.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型目录：把「平台配置 + 模型能力」解析成运行时模型。
 * 对外统一使用 provider/model 复合标识，未配置 api-key 的平台不参与解析与展示。
 */
/**
 * 负责 AI 模型选择与调用。
 */
@Component
@RequiredArgsConstructor
public class ModelCatalog {

    public enum Capability {
        TEXT("text", "文本模型"),
        IMAGE("image", "图片模型"),
        VIDEO("video", "视频模型");

        private final String key;
        private final String label;

        Capability(String key, String label) {
            this.key = key;
            this.label = label;
        }

        public String key() {
            return key;
        }

        public String label() {
            return label;
        }
    }

    /** 旧版本把供应商当模型用，保留兼容映射，保证存量任务可继续轮询 */
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "seedance", "ark/doubao-seedance-2-0-260128",
            "seedance-fast", "ark/doubao-seedance-2-0-fast-260128",
            "mock", "mock/mock-video");

    private final AiProperties properties;

    /** 前端下拉选项：平台内一个模型一行 */
    public record ModelOption(String value, String provider, String providerLabel, String model,
                              String label, List<String> ratios, List<String> resolutions,
                              List<Integer> durations) {
    }

    /** 解析后的运行时模型，协议适配器只依赖它，不再读取全局配置 */
    public record ResolvedModel(String providerKey, String providerLabel, String baseUrl, String apiKey,
                                String modelId, String modelLabel, String protocol,
                                List<String> ratios, List<String> resolutions, List<Integer> durations,
                                String defaultRatio, Integer defaultDuration, Boolean watermark) {

        public String key() {
            return providerKey + "/" + modelId;
        }

        public boolean mock() {
            return protocol != null && protocol.startsWith("mock");
        }
    }

    /** 按能力列出可用模型，未配置密钥的平台会被过滤 */
    public List<ModelOption> list(Capability capability) {
        List<ModelOption> options = new ArrayList<>();
        for (Map.Entry<String, AiProperties.Provider> entry : providerEntries().entrySet()) {
            if (!enabled(entry.getKey(), entry.getValue())) {
                continue;
            }
            for (AiProperties.Model model : models(entry.getValue(), capability)) {
                if (model == null || model.getId() == null || model.getId().isBlank()) {
                    continue;
                }
                options.add(new ModelOption(
                        entry.getKey() + "/" + model.getId(),
                        entry.getKey(),
                        providerLabel(entry.getKey(), entry.getValue()),
                        model.getId(),
                        modelLabel(model),
                        safe(model.getRatios()),
                        safe(model.getResolutions()),
                        safe(model.getDurations())));
            }
        }
        // 有真实平台可用时不再把 mock 混进下拉，mock 仅作为无密钥兜底
        List<ModelOption> real = options.stream()
                .filter(option -> !"mock".equals(option.provider()))
                .toList();
        return real.isEmpty() ? options : real;
    }

    /** 解析模型；requested 为空时取默认模型，非法或未启用时抛 IllegalArgumentException */
    public ResolvedModel resolve(Capability capability, String requested) {
        String value = requested == null ? "" : requested.trim();
        if (value.isEmpty()) {
            String fallback = defaultKey(capability);
            ResolvedModel resolved = fallback == null ? null : tryResolve(capability, ModelRef.parse(fallback));
            if (resolved == null) {
                throw new IllegalArgumentException("未配置可用的" + capability.label());
            }
            return resolved;
        }
        String alias = LEGACY_ALIASES.get(value.toLowerCase(Locale.ROOT));
        if (alias != null) {
            ResolvedModel resolved = tryResolve(capability, ModelRef.parse(alias));
            if (resolved == null) {
                throw new IllegalArgumentException("不支持的" + capability.label() + "：" + value);
            }
            return resolved;
        }
        ModelRef ref = value.contains("/") ? ModelRef.parse(value) : bareRef(capability, value);
        ResolvedModel resolved = tryResolve(capability, ref);
        if (resolved == null) {
            throw new IllegalArgumentException("不支持的" + capability.label() + "：" + value);
        }
        return resolved;
    }

    /** 默认模型标识；配置的默认值不可用时回退到第一个可用模型 */
    public String defaultKey(Capability capability) {
        String configured = switch (capability) {
            case TEXT -> defaults().getText();
            case IMAGE -> defaults().getImage();
            case VIDEO -> defaults().getVideo();
        };
        if (configured != null && !configured.isBlank()) {
            try {
                ResolvedModel resolved = tryResolve(capability, ModelRef.parse(configured));
                if (resolved != null) {
                    return resolved.key();
                }
            } catch (IllegalArgumentException ignored) {
                // 默认值写错时回退，避免启动后被一个坏配置卡死
            }
        }
        List<ModelOption> options = list(capability);
        return options.isEmpty() ? null : options.get(0).value();
    }

    /** 视频任务按存量字段解析：兼容 provider=seedance / provider=ark + model=xxx 两种历史形态 */
    public ResolvedModel resolveVideoTask(String provider, String model) {
        String legacy = provider == null ? null : LEGACY_ALIASES.get(provider.trim().toLowerCase(Locale.ROOT));
        if (legacy != null) {
            return resolve(Capability.VIDEO, legacy);
        }
        if (model != null && model.contains("/")) {
            return resolve(Capability.VIDEO, model);
        }
        if (provider != null && !provider.isBlank() && model != null && !model.isBlank()) {
            return resolve(Capability.VIDEO, provider.trim() + "/" + model.trim());
        }
        if (model != null && !model.isBlank()) {
            return resolve(Capability.VIDEO, model);
        }
        return resolve(Capability.VIDEO, null);
    }

    /** 只做展示用的模型标识拼接，不做可用性校验 */
    public String taskKey(String provider, String model) {
        if (model != null && model.contains("/")) {
            return model.trim();
        }
        String providerKey = provider == null ? "" : provider.trim();
        String legacy = LEGACY_ALIASES.get(providerKey.toLowerCase(Locale.ROOT));
        if (legacy != null) {
            return legacy;
        }
        if (!providerKey.isEmpty() && model != null && !model.isBlank()) {
            return providerKey + "/" + model.trim();
        }
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        return null;
    }

    public String providerLabel(String providerKey) {
        AiProperties.Provider provider = properties.getProviders().get(providerKey);
        return provider == null ? providerKey : providerLabel(providerKey, provider);
    }

    // ---------------------------------------------------------------- 内部实现

    private ResolvedModel tryResolve(Capability capability, ModelRef ref) {
        if (ref == null) {
            return null;
        }
        AiProperties.Provider provider = properties.getProviders().get(ref.provider());
        if (provider == null || !enabled(ref.provider(), provider)) {
            return null;
        }
        AiProperties.Model model = findModel(provider, capability, ref.model());
        if (model == null) {
            return null;
        }
        String protocol = model.getProtocol();
        if (protocol == null || protocol.isBlank()) {
            protocol = provider.getProtocols() == null ? null : provider.getProtocols().get(capability.key());
        }
        if (protocol == null || protocol.isBlank()) {
            throw new IllegalArgumentException("模型 " + ref.key() + " 未配置 " + capability.key() + " 协议");
        }
        return new ResolvedModel(
                ref.provider(),
                providerLabel(ref.provider(), provider),
                provider.getBaseUrl(),
                provider.getApiKey(),
                model.getId(),
                modelLabel(model),
                protocol,
                safe(model.getRatios()),
                safe(model.getResolutions()),
                safe(model.getDurations()),
                model.getDefaultRatio(),
                model.getDefaultDuration(),
                model.getWatermark());
    }

    private ModelRef bareRef(Capability capability, String model) {
        for (Map.Entry<String, AiProperties.Provider> entry : providerEntries().entrySet()) {
            if (enabled(entry.getKey(), entry.getValue())
                    && findModel(entry.getValue(), capability, model) != null) {
                return new ModelRef(entry.getKey(), model);
            }
        }
        String fallback = properties.getDefaultProvider();
        return new ModelRef(fallback == null || fallback.isBlank() ? "mock" : fallback.trim(), model);
    }

    private Map<String, AiProperties.Provider> providerEntries() {
        Map<String, AiProperties.Provider> providers = properties.getProviders();
        return providers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providers);
    }

    private boolean enabled(String key, AiProperties.Provider provider) {
        if (provider == null) {
            return false;
        }
        return "mock".equals(key) || (provider.getApiKey() != null && !provider.getApiKey().isBlank());
    }

    private List<AiProperties.Model> models(AiProperties.Provider provider, Capability capability) {
        List<AiProperties.Model> models = switch (capability) {
            case TEXT -> provider.getTextModels();
            case IMAGE -> provider.getImageModels();
            case VIDEO -> provider.getVideoModels();
        };
        return models == null ? List.of() : models;
    }

    private AiProperties.Model findModel(AiProperties.Provider provider, Capability capability, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        for (AiProperties.Model model : models(provider, capability)) {
            if (model != null && model.getId() != null && model.getId().equalsIgnoreCase(modelId.trim())) {
                return model;
            }
        }
        return null;
    }

    private AiProperties.Defaults defaults() {
        return properties.getDefaults() == null ? new AiProperties.Defaults() : properties.getDefaults();
    }

    private String providerLabel(String key, AiProperties.Provider provider) {
        return provider.getLabel() == null || provider.getLabel().isBlank() ? key : provider.getLabel();
    }

    private String modelLabel(AiProperties.Model model) {
        return model.getLabel() == null || model.getLabel().isBlank() ? model.getId() : model.getLabel();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
