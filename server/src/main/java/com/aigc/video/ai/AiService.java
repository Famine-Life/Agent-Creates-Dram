package com.aigc.video.ai;

import com.aigc.video.storage.AssetService;
import com.aigc.video.storage.StoredAsset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 能力网关：分镜脚本（LLM）与图片生成。
 * 通过 {@link ModelCatalog} 按 provider/model 解析平台与协议，未配置密钥时回退 mock。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final String SCRIPT_SYSTEM_PROMPT = """
            你是资深影视分镜师。根据用户给出的剧情描述，输出可直接用于 AI 视频生成的分镜脚本。
            严格只返回 JSON，不要 Markdown 代码块，不要解释。JSON 结构：
            {"title":"分镜标题","shots":[{"title":"镜头标题","prompt":"单个镜头的详细提示词"}]}
            要求：3-6 个镜头；prompt 使用中文，包含景别、画面主体、动作、光影氛围、镜头运动，不要出现 @ 引用。
            """;

    private static final String OPTIMIZE_SYSTEM_PROMPT = """
            你是资深 AI 视频提示词优化师。把用户输入的创意改写成可直接用于视频生成的高质量中文提示词。
            要求：保留原意与画面主体；补充环境、动作、景别、光影氛围、镜头运动、画面质感；
            只输出优化后的提示词正文，不要解释、不要 Markdown、不要标题、不要分点。
            """;

    private final ModelCatalog modelCatalog;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;

    /** 按 base-url 复用 RestClient，不同平台互不影响 */
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public record ScriptShot(String title, String prompt) {
    }

    public record ScriptResult(String title, List<ScriptShot> shots) {
    }

    /** 生成分镜脚本，model 为 provider/model 或裸模型 ID，为空时使用默认文本模型 */
    public ScriptResult generateScript(String prompt, String model) {
        ModelCatalog.ResolvedModel resolved = modelCatalog.resolve(ModelCatalog.Capability.TEXT, model);
        if (resolved.mock()) {
            return mockScript(prompt);
        }
        requireProtocol(resolved, "openai-compatible", "文本");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolved.modelId());
        body.put("temperature", 0.7);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SCRIPT_SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)));
        JsonNode response = postChat(resolved, body);
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("AI 脚本生成返回为空：" + response.path("error").path("message").asText(""));
        }
        return parseScript(content);
    }

    /** 一键优化视频提示词，model 为 provider/model 或裸模型 ID，为空时使用默认文本模型 */
    public String optimizePrompt(String prompt, String model) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        String text = prompt.trim();
        ModelCatalog.ResolvedModel resolved = modelCatalog.resolve(ModelCatalog.Capability.TEXT, model);
        if (resolved.mock()) {
            return mockOptimize(text);
        }
        requireProtocol(resolved, "openai-compatible", "文本");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolved.modelId());
        body.put("temperature", 0.8);
        body.put("messages", List.of(
                Map.of("role", "system", "content", OPTIMIZE_SYSTEM_PROMPT),
                Map.of("role", "user", "content", text)));
        JsonNode response = postChatWithRetry(resolved, body, 2);
        String content = response.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IllegalStateException("AI 提示词优化返回为空："
                    + response.path("error").path("message").asText(""));
        }
        return content;
    }

    /** 生成图片，返回可直接展示的地址；referenceUrls 非空时按顺序作为参考图做图生图 */
    public String generateImage(String prompt, String model, List<String> referenceUrls, String size) {
        ModelCatalog.ResolvedModel resolved = modelCatalog.resolve(ModelCatalog.Capability.IMAGE, model);
        if (resolved.mock()) {
            return storeMockImage(prompt);
        }
        String targetSize = size == null || size.isBlank() ? "1280x720" : size;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolved.modelId());
        body.put("prompt", prompt);
        switch (resolved.protocol()) {
            case "ark-image" -> {
                body.put("size", targetSize);
                body.put("response_format", "url");
                body.put("watermark", false);
                List<String> images = referenceUrls == null ? List.of() : referenceUrls.stream()
                        .filter(url -> url != null && !url.isBlank())
                        .map(assetService::providerImageUrl)
                        .toList();
                if (images.size() == 1) {
                    body.put("image", images.get(0));
                } else if (!images.isEmpty()) {
                    body.put("image", images);
                }
            }
            case "openai-image" -> {
                body.put("n", 1);
                body.put("size", targetSize);
            }
            default -> throw new IllegalArgumentException("不支持的图片协议：" + resolved.protocol());
        }
        JsonNode response = client(resolved).post()
                .uri("/images/generations")
                .header("Authorization", "Bearer " + apiKey(resolved))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("AI 图片生成无响应");
        }
        JsonNode first = response.path("data").path(0);
        String url = first.path("url").asText("");
        if (!url.isBlank()) {
            return assetService.transfer(url, "images", ".png");
        }
        String base64 = first.path("b64_json").asText("");
        if (base64.isBlank()) {
            throw new IllegalStateException("AI 图片生成失败：" + response.path("error").path("message").asText("未返回图片"));
        }
        StoredAsset stored = assetService.storeImage("ai-image.png", "image/png",
                java.util.Base64.getDecoder().decode(base64));
        return stored.url();
    }

    // ---------------------------------------------------------------- 协议调用

    private JsonNode postChat(ModelCatalog.ResolvedModel model, Map<String, Object> body) {
        JsonNode response = client(model).post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey(model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("AI 文本生成无响应");
        }
        return response;
    }

    /** 供应商突发限流（429）时短暂退避重试，避免用户点一次优化就直接报错 */
    private JsonNode postChatWithRetry(ModelCatalog.ResolvedModel model, Map<String, Object> body, int retries) {
        long delay = 1500L;
        for (int attempt = 0; ; attempt++) {
            try {
                return postChat(model, body);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 429) {
                    throw e;
                }
                if (attempt >= retries) {
                    throw new IllegalStateException("AI 请求过于频繁，请稍后再试", e);
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("AI 请求被中断", interrupted);
                }
                delay *= 2;
            }
        }
    }

    private void requireProtocol(ModelCatalog.ResolvedModel model, String expected, String label) {
        if (!expected.equals(model.protocol())) {
            throw new IllegalArgumentException("不支持的" + label + "协议：" + model.protocol());
        }
    }

    private String apiKey(ModelCatalog.ResolvedModel model) {
        if (model.apiKey() == null || model.apiKey().isBlank()) {
            throw new IllegalStateException("模型 " + model.key() + " 未配置 api-key");
        }
        return model.apiKey();
    }

    private RestClient client(ModelCatalog.ResolvedModel model) {
        if (model.baseUrl() == null || model.baseUrl().isBlank()) {
            throw new IllegalStateException("模型 " + model.key() + " 未配置 base-url");
        }
        String baseUrl = model.baseUrl().trim();
        return clients.computeIfAbsent(baseUrl, url -> RestClient.builder().baseUrl(url).build());
    }

    // ---------------------------------------------------------------- mock

    private ScriptResult mockScript(String prompt) {
        String theme = shorten(prompt == null ? "未命名剧情" : prompt.trim(), 18);
        List<ScriptShot> shots = new ArrayList<>();
        shots.add(new ScriptShot("远景 · 环境建立",
                theme + "，大远景，交代环境全貌与时间氛围，自然光，镜头缓慢向前推进"));
        shots.add(new ScriptShot("中景 · 主体登场",
                theme + "，中景，画面主体走入镜头并停顿，侧逆光勾勒轮廓，镜头轻微跟随"));
        shots.add(new ScriptShot("近景 · 情绪推进",
                theme + "，近景，人物面部情绪变化，柔光与浅景深，镜头缓慢推近"));
        shots.add(new ScriptShot("特写 · 收束",
                theme + "，特写细节，光线收暗，镜头缓慢拉远并定格"));
        return new ScriptResult("分镜 · " + theme, shots);
    }

    private String mockOptimize(String prompt) {
        return prompt + "。电影级画面，主体细节清晰、材质真实，环境层次丰富，柔和侧逆光勾勒轮廓，"
                + "浅景深突出主体，镜头缓慢向前推进，冷暖色调对比，高动态范围，实拍质感。";
    }

    private String storeMockImage(String prompt) {
        String text = prompt == null || prompt.isBlank() ? "未填写提示词" : prompt.trim();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1024\" height=\"576\" viewBox=\"0 0 1024 576\">");
        svg.append("<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"#1b1f4b\"/><stop offset=\"0.55\" stop-color=\"#4b2a6b\"/>")
                .append("<stop offset=\"1\" stop-color=\"#0a0b0d\"/></linearGradient></defs>");
        svg.append("<rect width=\"1024\" height=\"576\" fill=\"url(#g)\"/>");
        svg.append("<circle cx=\"820\" cy=\"120\" r=\"180\" fill=\"#7c6bff\" opacity=\"0.22\"/>");
        svg.append("<circle cx=\"180\" cy=\"470\" r=\"220\" fill=\"#f0b429\" opacity=\"0.10\"/>");
        svg.append("<text x=\"60\" y=\"92\" fill=\"#c9c0ff\" font-size=\"22\" font-family=\"sans-serif\">AI 图片 · Mock 预览</text>");
        int y = 190;
        for (String line : wrap(text, 22)) {
            svg.append("<text x=\"60\" y=\"").append(y)
                    .append("\" fill=\"#f2f3f7\" font-size=\"40\" font-family=\"sans-serif\">")
                    .append(escapeXml(line)).append("</text>");
            y += 58;
        }
        svg.append("<text x=\"60\" y=\"520\" fill=\"#8d939e\" font-size=\"20\" font-family=\"sans-serif\">配置平台 API Key 后将调用真实图片模型</text>");
        svg.append("</svg>");
        StoredAsset stored = assetService.storeImage("ai-image.svg", "image/svg+xml",
                svg.toString().getBytes(StandardCharsets.UTF_8));
        return stored.url();
    }

    private List<String> wrap(String text, int size) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < text.length() && lines.size() < 5; i += size) {
            lines.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return lines;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String shorten(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    // ---------------------------------------------------------------- 解析

    private ScriptResult parseScript(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            String title = root.path("title").asText("AI 分镜脚本");
            List<ScriptShot> shots = new ArrayList<>();
            for (JsonNode shot : root.path("shots")) {
                String prompt = shot.path("prompt").asText("");
                if (prompt.isBlank()) {
                    continue;
                }
                String shotTitle = shot.path("title").asText("镜头 " + (shots.size() + 1));
                shots.add(new ScriptShot(shotTitle, prompt.trim()));
            }
            if (shots.isEmpty()) {
                throw new IllegalStateException("AI 返回的分镜为空");
            }
            return new ScriptResult(title.isBlank() ? "AI 分镜脚本" : title, shots);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 分镜 JSON 解析失败：{}，原始内容={}", e.getMessage(), raw);
            throw new IllegalStateException("AI 返回内容无法解析为分镜脚本");
        }
    }
}
