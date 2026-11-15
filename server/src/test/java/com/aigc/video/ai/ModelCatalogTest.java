package com.aigc.video.ai;

import com.aigc.video.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelCatalogTest {

    private final AiProperties properties = properties();
    private final ModelCatalog catalog = new ModelCatalog(properties);

    @Test
    void resolvesDefaultModelWithProtocol() {
        ModelCatalog.ResolvedModel model = catalog.resolve(ModelCatalog.Capability.TEXT, null);
        assertEquals("ark/text-a", model.key());
        assertEquals("openai-compatible", model.protocol());
        assertEquals("https://ark.example.com", model.baseUrl());
    }

    @Test
    void resolvesCompositeKeyAndBareModelId() {
        assertEquals("ark/text-a", catalog.resolve(ModelCatalog.Capability.TEXT, "ark/text-a").key());
        assertEquals("ark/text-a", catalog.resolve(ModelCatalog.Capability.TEXT, "text-a").key());
        assertEquals("ark/doubao-seedance-2-0-260128",
                catalog.resolve(ModelCatalog.Capability.VIDEO, "doubao-seedance-2-0-260128").key());
    }

    @Test
    void rejectsUnknownModelAndDisabledProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> catalog.resolve(ModelCatalog.Capability.TEXT, "ark/not-exist"));
        // OpenAI 未配置 api-key，视为未启用
        assertThrows(IllegalArgumentException.class,
                () -> catalog.resolve(ModelCatalog.Capability.TEXT, "openai/gpt-4.1"));
    }

    @Test
    void fallsBackToFirstEnabledProviderWhenDefaultUnavailable() {
        properties.getProviders().get("ark").setApiKey("");
        assertEquals("mock/mock-text", catalog.defaultKey(ModelCatalog.Capability.TEXT));
        assertEquals("mock/mock-text", catalog.resolve(ModelCatalog.Capability.TEXT, null).key());
    }

    @Test
    void mapsLegacyVideoAliases() {
        assertEquals("ark/doubao-seedance-2-0-260128",
                catalog.resolve(ModelCatalog.Capability.VIDEO, "seedance").key());
        assertEquals("ark/doubao-seedance-2-0-fast-260128",
                catalog.resolveVideoTask("seedance-fast", "seedance-fast").key());
        assertEquals("ark/doubao-seedance-2-0-260128",
                catalog.resolveVideoTask("ark", "doubao-seedance-2-0-260128").key());
        assertEquals("ark/doubao-seedance-2-0-260128", catalog.taskKey("seedance", "seedance"));
        assertEquals("ark/doubao-seedance-2-0-260128",
                catalog.taskKey("ark", "doubao-seedance-2-0-260128"));
    }

    @Test
    void listsEnabledProvidersAndFallsBackToMock() {
        List<String> keys = catalog.list(ModelCatalog.Capability.TEXT).stream()
                .map(ModelCatalog.ModelOption::value)
                .toList();
        // 有真实平台时不混入 mock
        assertEquals(List.of("ark/text-a"), keys);

        properties.getProviders().get("ark").setApiKey("");
        List<String> fallback = catalog.list(ModelCatalog.Capability.TEXT).stream()
                .map(ModelCatalog.ModelOption::value)
                .toList();
        assertEquals(List.of("mock/mock-text"), fallback);
    }

    @Test
    void keepsVideoCapabilityOfModel() {
        ModelCatalog.ResolvedModel model = catalog.resolve(ModelCatalog.Capability.VIDEO,
                "ark/doubao-seedance-2-0-260128");
        assertEquals(List.of("16:9", "9:16"), model.ratios());
        assertEquals(List.of("480P", "720P"), model.resolutions());
        assertEquals(List.of(4, 5), model.durations());
        assertEquals(5, model.defaultDuration());
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        AiProperties.Defaults defaults = new AiProperties.Defaults();
        defaults.setText("ark/text-a");
        defaults.setImage("ark/image-a");
        defaults.setVideo("ark/doubao-seedance-2-0-260128");
        properties.setDefaults(defaults);

        AiProperties.Provider ark = provider("火山方舟", "https://ark.example.com", "sk-ark");
        ark.getProtocols().put("text", "openai-compatible");
        ark.getProtocols().put("image", "ark-image");
        ark.getProtocols().put("video", "ark-video");
        ark.getTextModels().add(model("text-a", "Text A"));
        ark.getImageModels().add(model("image-a", "Image A"));
        AiProperties.Model video = model("doubao-seedance-2-0-260128", "Seedance 2.0");
        video.setRatios(List.of("16:9", "9:16"));
        video.setResolutions(List.of("480P", "720P"));
        video.setDurations(List.of(4, 5));
        video.setDefaultDuration(5);
        ark.getVideoModels().add(video);
        ark.getVideoModels().add(model("doubao-seedance-2-0-fast-260128", "Seedance 2.0 Fast"));

        AiProperties.Provider openai = provider("OpenAI", "https://api.openai.com/v1", "");
        openai.getProtocols().put("text", "openai-compatible");
        openai.getTextModels().add(model("gpt-4.1", "GPT-4.1"));

        AiProperties.Provider mock = provider("Mock", null, "mock");
        mock.getProtocols().put("text", "mock");
        mock.getProtocols().put("video", "mock-video");
        mock.getTextModels().add(model("mock-text", "Mock 文本"));
        mock.getVideoModels().add(model("mock-video", "Mock 视频"));

        properties.getProviders().put("ark", ark);
        properties.getProviders().put("openai", openai);
        properties.getProviders().put("mock", mock);
        return properties;
    }

    private static AiProperties.Provider provider(String label, String baseUrl, String apiKey) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setLabel(label);
        provider.setBaseUrl(baseUrl);
        provider.setApiKey(apiKey);
        return provider;
    }

    private static AiProperties.Model model(String id, String label) {
        AiProperties.Model model = new AiProperties.Model();
        model.setId(id);
        model.setLabel(label);
        return model;
    }
}
