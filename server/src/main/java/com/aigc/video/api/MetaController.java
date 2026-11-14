package com.aigc.video.api;

import com.aigc.video.config.AiProperties;
import com.aigc.video.config.ProviderProperties;
import com.aigc.video.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

/** 顶栏展示用的运行时信息 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final ProviderProperties providerProperties;
    private final StorageProperties storageProperties;
    private final AiProperties aiProperties;

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of(
                "provider", providerProperties.getDefaultName(),
                "providers", new String[]{"mock", "seedance"},
                "storage", storageProperties.getType(),
                "mcpEndpoint", ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/sse")
                        .build()
                        .toUriString(),
                "mcpTransport", "sse",
                "wsPath", "/ws/canvas/{workspaceId}",
                "aiMock", aiProperties.mockEnabled(),
                "scriptModel", aiProperties.mockEnabled() ? "AI 脚本 · Mock" : aiProperties.getScriptModel(),
                "imageModel", aiProperties.mockEnabled() ? "AI 图片 · Mock" : aiProperties.getImageModel()
        );
    }
}
