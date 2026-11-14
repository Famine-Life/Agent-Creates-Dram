package com.aigc.video.provider;

import com.aigc.video.config.ProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟 · 即梦（Seedance）视频生成。
 * 文档：创建任务 POST /contents/generations/tasks，查询 GET /contents/generations/tasks/{id}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedanceVideoProvider implements VideoProvider {

    private final ProviderProperties properties;

    private volatile RestClient client;

    @Override
    public String name() {
        return "seedance";
    }

    @Override
    public SubmitResult submit(SubmitCommand command) {
        ProviderProperties.Seedance cfg = properties.getSeedance();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置 ARK_API_KEY，无法使用 seedance 供应商");
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildText(command, cfg)));
        for (String imageUrl : command.imageUrls()) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("content", content);

        JsonNode response = client().post()
                .uri("/contents/generations/tasks")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("id").isMissingNode()) {
            throw new IllegalStateException("seedance 提交失败：响应缺少 id，原始响应=" + response);
        }
        String taskId = response.path("id").asText();
        log.info("[seedance] 提交成功 providerTaskId={} 参考图={}张", taskId, command.imageUrls().size());
        return new SubmitResult(taskId);
    }

    @Override
    public PollResult poll(String providerTaskId) {
        ProviderProperties.Seedance cfg = properties.getSeedance();
        JsonNode response = client().get()
                .uri("/contents/generations/tasks/{id}", providerTaskId)
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return PollResult.running(10);
        }
        String status = response.path("status").asText("");
        return switch (status) {
            case "succeeded" -> {
                String videoUrl = response.path("content").path("video_url").asText(null);
                String coverUrl = response.path("content").path("last_frame_url").asText(null);
                if (videoUrl == null || videoUrl.isBlank()) {
                    yield PollResult.failed("seedance 已成功但未返回视频地址");
                }
                yield PollResult.succeeded(videoUrl, coverUrl);
            }
            case "failed", "cancelled" -> {
                String message = response.path("error").path("message").asText(status);
                yield PollResult.failed("seedance 生成失败：" + message);
            }
            case "queued" -> PollResult.running(5);
            default -> PollResult.running(60);
        };
    }

    private String buildText(SubmitCommand command, ProviderProperties.Seedance cfg) {
        StringBuilder sb = new StringBuilder(command.prompt() == null ? "" : command.prompt());
        String ratio = command.ratio() == null || command.ratio().isBlank() ? cfg.getRatio() : command.ratio();
        Integer duration = command.duration() == null ? cfg.getDuration() : command.duration();
        appendParam(sb, "ratio", ratio);
        appendParam(sb, "duration", duration == null ? null : String.valueOf(duration));
        appendParam(sb, "resolution", command.resolution());
        appendParam(sb, "watermark", cfg.getWatermark() == null ? null : String.valueOf(cfg.getWatermark()));
        return sb.toString();
    }

    private void appendParam(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(" --").append(key).append(' ').append(value);
    }

    private RestClient client() {
        RestClient local = client;
        if (local == null) {
            synchronized (this) {
                if (client == null) {
                    client = RestClient.builder()
                            .baseUrl(properties.getSeedance().getBaseUrl())
                            .build();
                }
                local = client;
            }
        }
        return local;
    }
}
