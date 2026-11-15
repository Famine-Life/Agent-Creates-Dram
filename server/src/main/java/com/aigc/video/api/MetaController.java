package com.aigc.video.api;

import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** 顶栏展示用的运行时信息与模型目录 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final ModelCatalog modelCatalog;
    private final StorageProperties storageProperties;

    @GetMapping
    public Map<String, Object> meta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        String textDefault = modelCatalog.defaultKey(ModelCatalog.Capability.TEXT);
        String imageDefault = modelCatalog.defaultKey(ModelCatalog.Capability.IMAGE);
        String videoDefault = modelCatalog.defaultKey(ModelCatalog.Capability.VIDEO);
        String videoProvider = providerOf(videoDefault);

        meta.put("provider", videoProvider);
        meta.put("providerLabel", videoProvider == null ? null : modelCatalog.providerLabel(videoProvider));
        meta.put("storage", storageProperties.getType());
        meta.put("mcpEndpoint", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/sse")
                .build()
                .toUriString());
        meta.put("mcpTransport", "sse");
        meta.put("wsPath", "/ws/canvas/{workspaceId}");
        meta.put("aiMock", "mock".equals(providerOf(textDefault)));

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("text", textDefault);
        defaults.put("image", imageDefault);
        defaults.put("video", videoDefault);
        meta.put("defaults", defaults);

        // 每个选项带 provider / providerLabel，前端按下拉分组展示
        meta.put("textModels", modelCatalog.list(ModelCatalog.Capability.TEXT));
        meta.put("imageModels", modelCatalog.list(ModelCatalog.Capability.IMAGE));
        meta.put("videoModels", modelCatalog.list(ModelCatalog.Capability.VIDEO));
        return meta;
    }

    private String providerOf(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return null;
        }
        int slash = modelKey.indexOf('/');
        return slash > 0 ? modelKey.substring(0, slash) : modelKey;
    }
}
