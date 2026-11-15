package com.aigc.video.provider;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火山方舟视频协议（Seedance）。
 * 文档：创建任务 POST /contents/generations/tasks，查询 GET /contents/generations/tasks/{id}
 */
@Slf4j
@Component
public class ArkVideoProvider implements VideoProvider {

    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "ark-video";
    }

    @Override
    public SubmitResult submit(SubmitCommand command) {
        VideoContext context = requireContext(command);
        String apiKey = apiKey(context);
        String model = context.model();
        boolean seedance2 = model != null && model.contains("seedance-2-0");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text",
                seedance2 ? command.prompt() : buildText(command, context)));
        for (String imageUrl : command.imageUrls()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "image_url");
            item.put("image_url", Map.of("url", imageUrl));
            if (seedance2) {
                item.put("role", "reference_image");
            }
            content.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("content", content);
        if (seedance2) {
            body.put("resolution", apiResolution(command.resolution()));
            body.put("ratio", blankToDefault(command.ratio(), "16:9"));
            body.put("duration", command.duration() == null ? 5 : command.duration());
            body.put("watermark", Boolean.TRUE.equals(context.watermark()));
        }

        JsonNode response = client(context).post()
                .uri("/contents/generations/tasks")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("id").isMissingNode()) {
            throw new IllegalStateException("视频提交失败：响应缺少 id，原始响应=" + response);
        }
        String taskId = response.path("id").asText();
        log.info("[{}] 提交成功 providerTaskId={} model={} 参考图={}张",
                name(), taskId, model, command.imageUrls().size());
        return new SubmitResult(taskId);
    }

    @Override
    public PollResult poll(String providerTaskId, VideoContext context) {
        String apiKey = apiKey(context);
        JsonNode response = client(context).get()
                .uri("/contents/generations/tasks/{id}", providerTaskId)
                .header("Authorization", "Bearer " + apiKey)
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
                    yield PollResult.failed("视频已成功但未返回视频地址");
                }
                yield PollResult.succeeded(videoUrl, coverUrl);
            }
            case "failed", "cancelled" -> {
                String message = response.path("error").path("message").asText(status);
                yield PollResult.failed("视频生成失败：" + message);
            }
            case "queued" -> PollResult.running(5);
            default -> PollResult.running(60);
        };
    }

    /** 取消或删除方舟侧任务：DELETE /contents/generations/tasks/{id} */
    @Override
    public void cancel(String providerTaskId, VideoContext context) {
        if (providerTaskId == null || providerTaskId.isBlank()
                || context == null || context.apiKey() == null || context.apiKey().isBlank()) {
            return;
        }
        client(context).delete()
                .uri("/contents/generations/tasks/{id}", providerTaskId)
                .header("Authorization", "Bearer " + apiKey(context))
                .retrieve()
                .toBodilessEntity();
        log.info("[{}] 已请求方舟取消/删除任务 providerTaskId={}", name(), providerTaskId);
    }

    private VideoContext requireContext(SubmitCommand command) {
        VideoContext context = command == null ? null : command.context();
        if (context == null || context.model() == null || context.model().isBlank()) {
            throw new IllegalStateException("提交视频任务缺少模型上下文");
        }
        return context;
    }

    private String apiKey(VideoContext context) {
        if (context.apiKey() == null || context.apiKey().isBlank()) {
            throw new IllegalStateException("模型 " + context.provider() + "/" + context.model() + " 未配置 api-key");
        }
        return context.apiKey();
    }

    private String apiResolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            return "720p";
        }
        return "4K".equalsIgnoreCase(resolution) ? "4K" : resolution.toLowerCase();
    }

    private String buildText(SubmitCommand command, VideoContext context) {
        StringBuilder sb = new StringBuilder(command.prompt() == null ? "" : command.prompt());
        appendParam(sb, "ratio", blankToDefault(command.ratio(), "16:9"));
        appendParam(sb, "duration", String.valueOf(command.duration() == null ? 5 : command.duration()));
        appendParam(sb, "resolution", command.resolution());
        appendParam(sb, "watermark", String.valueOf(Boolean.TRUE.equals(context.watermark())));
        return sb.toString();
    }

    private void appendParam(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(" --").append(key).append(' ').append(value);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private RestClient client(VideoContext context) {
        if (context.baseUrl() == null || context.baseUrl().isBlank()) {
            throw new IllegalStateException("模型 " + context.provider() + "/" + context.model() + " 未配置 base-url");
        }
        String baseUrl = context.baseUrl().trim();
        return clients.computeIfAbsent(baseUrl, url -> RestClient.builder().baseUrl(url).build());
    }
}
