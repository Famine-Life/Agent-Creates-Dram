package com.aigc.video.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts provider-specific failures into concise, actionable UI messages. */
@Component
@RequiredArgsConstructor
public class ProviderErrorHintResolver {
    private static final Pattern REQUEST_ID = Pattern.compile("(?i)(?:ark\\s*)?request\\s*id\\s*[:=]\\s*([a-z0-9-]+)");
    private static final Pattern HTTP_STATUS = Pattern.compile("(?<!\\d)(\\d{3})\\s+(?:Bad Request|Unauthorized|Forbidden|Not Found|Too Many Requests|Internal Server Error|Bad Gateway|Service Unavailable|Gateway Timeout)", Pattern.CASE_INSENSITIVE);

    /** Add provider-specific code mappings here; unrecognized providers retain a safe generic fallback. */
    private static final Map<String, Map<String, String>> PROVIDER_HINTS = Map.of(
            "ark", Map.ofEntries(
                    Map.entry("InputImageSensitiveContentDetected.PrivacyInformation", "火山方舟判定参考图片可能包含真人，因此拦截了请求。请换成非写实、非真人的插画参考图，或移除该人物图片后重试。若认为是误判，请保留请求 ID 联系平台支持。"),
                    Map.entry("InputVideoSensitiveContentDetected.PrivacyInformation", "火山方舟判定输入视频可能包含真人并拦截请求。请更换为非真人视频后重试。"),
                    Map.entry("InputImageSensitiveContentDetected.PolicyViolation", "参考图片可能涉及版权限制。请改用自有或已获授权的图片后重试。"),
                    Map.entry("InputTextSensitiveContentDetected.PolicyViolation", "提示词可能涉及版权限制。请改写为原创描述，避免直接引用受版权保护的角色、作品或文本后重试。"),
                    Map.entry("InputVideoSensitiveContentDetected.PolicyViolation", "参考视频可能涉及版权限制。请改用自有或已获授权的视频后重试。"),
                    Map.entry("InputAudioSensitiveContentDetected.PolicyViolation", "参考音频可能涉及版权限制。请改用自有或已获授权的音频后重试。"),
                    Map.entry("InputImageSensitiveContentDetected", "参考图片触发了平台安全检查。请更换图片或调整图片内容后重试。"),
                    Map.entry("InputVideoSensitiveContentDetected", "输入视频触发了平台安全检查。请更换视频后重试。"),
                    Map.entry("InputAudioSensitiveContentDetected", "输入音频触发了平台安全检查。请更换音频后重试。"),
                    Map.entry("InputTextRiskDetection", "提示词触发了平台内容安全检查。请调整描述后重试。"),
                    Map.entry("SensitiveContentDetected", "输入文本触发了平台内容安全检查。请调整提示词后重试。"),
                    Map.entry("ContentSecurityDetectionError", "火山方舟内容安全服务暂时异常，请稍后重试；若持续发生，请带请求 ID 联系平台支持。"),
                    Map.entry("InputImageRiskDetection", "参考图片触发了平台内容安全检查。请更换图片后重试。"),
                    Map.entry("OutputTextSensitiveContentDetected", "平台判定生成文本包含敏感内容。请调整提示词后重试。"),
                    Map.entry("OutputImageSensitiveContentDetected", "平台判定生成图片包含敏感内容。请调整提示词或参考图片后重试。"),
                    Map.entry("OutputVideoSensitiveContentDetected", "平台判定生成视频包含敏感内容。请调整提示词或参考图片后重试。"),
                    Map.entry("OutputAudioSensitiveContentDetected", "平台判定生成音频包含敏感内容。请调整提示词或参考音频后重试。"),
                    Map.entry("OutputImageSensitiveContentDetected.PolicyViolation", "生成图片可能涉及版权限制。请调整提示词，改用原创描述后重试。"),
                    Map.entry("OutputVideoSensitiveContentDetected.PolicyViolation", "生成视频可能涉及版权限制。请调整提示词，改用原创描述后重试。"),
                    Map.entry("InvalidParameter", "请求参数不符合该模型的要求。请检查画幅、时长、分辨率和参考素材数量后重试。"),
                    Map.entry("MissingParameter", "请求缺少模型必填参数。请检查提示词、画幅、时长和参考素材配置。"),
                    Map.entry("InvalidEndpoint.ClosedEndpoint", "火山方舟接入点已关闭或暂时不可用。请检查接入点状态，或稍后重试。"),
                    Map.entry("OperationDenied.ServiceOverdue", "火山方舟账户欠费或余额不足，当前请求无法执行。请检查账户余额或充值后重试。"),
                    Map.entry("AuthenticationError", "火山方舟身份验证失败。请检查本机配置中的 API Key 是否有效。"),
                    Map.entry("AccessDenied", "当前火山方舟账号或项目没有调用此模型的权限。请检查模型开通状态和项目授权。"),
                    Map.entry("ModelNotFound", "未找到指定模型或模型接入点。请核对模型 ID 和接入点配置。"),
                    Map.entry("RateLimitExceeded", "请求频率超过火山方舟限制。请稍后再试，或降低并发。"),
                    Map.entry("QuotaExceeded", "火山方舟模型额度或并发配额已用完。请检查配额，或稍后重试。"),
                    Map.entry("InternalError", "火山方舟服务暂时异常。请稍后重试；若持续发生，请带请求 ID 联系平台支持。"),
                    Map.entry("ServiceUnavailable", "火山方舟服务暂时不可用。请稍后重试。")
            ),
            "openai", Map.ofEntries(
                    Map.entry("content_policy_violation", "模型安全策略拦截了输入或输出。请调整提示词或参考素材后重试。"),
                    Map.entry("invalid_api_key", "OpenAI 身份验证失败。请检查本机配置中的 API Key。"),
                    Map.entry("model_not_found", "OpenAI 未找到该模型或当前账号无权使用。请检查模型 ID 与账号权限。"),
                    Map.entry("rate_limit_exceeded", "OpenAI 请求频率或额度达到限制。请检查配额并稍后重试。")
            ),
            "dashscope", Map.ofEntries(
                    Map.entry("DataInspectionFailed", "百炼内容安全检查拦截了请求。请调整提示词或参考素材后重试。"),
                    Map.entry("InvalidApiKey", "百炼身份验证失败。请检查本机配置中的 API Key。"),
                    Map.entry("Throttling", "百炼请求频率达到限制。请稍后重试或降低并发。")
            )
    );

    private final ObjectMapper objectMapper;

    public String resolve(String provider, String rawError) {
        if (rawError == null || rawError.isBlank()) return "模型服务返回未知错误，请稍后重试。";
        JsonNode error = parseError(rawError);
        String code = firstText(error, "code", "Code", "error_code", "errorCode");
        String message = firstText(error, "message", "Message", "error_description", "detail");
        String requestId = firstText(error, "request_id", "requestId", "RequestId", "ark_request_id");
        if (requestId == null) requestId = requestIdFrom(rawError);

        String hint = lookup(provider, code);
        if (hint == null) hint = lookupHttpStatus(rawError);
        if (hint == null) hint = message == null || message.isBlank()
                ? "模型服务返回了暂未识别的错误，请检查服务配置后重试。"
                : "模型服务返回错误：" + concise(message);
        if (requestId != null && !requestId.isBlank()) hint += " 请求 ID：" + concise(requestId);
        return hint;
    }

    private String lookup(String provider, String code) {
        if (code == null || code.isBlank()) return null;
        Map<String, String> hints = PROVIDER_HINTS.get(provider == null ? "" : provider.toLowerCase(Locale.ROOT));
        if (hints == null) return null;
        String hint = hints.get(code);
        int dot = code.indexOf('.');
        return hint != null ? hint : dot > 0 ? hints.get(code.substring(0, dot)) : null;
    }

    private String lookupHttpStatus(String raw) {
        Matcher matcher = HTTP_STATUS.matcher(raw);
        if (!matcher.find()) return null;
        return switch (matcher.group(1)) {
            case "401" -> "模型服务身份验证失败。请检查本机配置中的 API Key。";
            case "403" -> "模型服务拒绝了请求，通常是账号、项目或模型权限不足。请检查服务授权。";
            case "404" -> "模型或接入点不存在。请核对模型 ID 与服务地址。";
            case "429" -> "模型服务限流或额度不足。请检查配额、降低并发或稍后重试。";
            case "500", "502", "503", "504" -> "模型服务暂时异常。请稍后重试；若持续发生，请联系服务管理员。";
            case "400" -> "模型服务认为请求参数或输入素材不符合要求。请检查提示词、画幅、时长和参考素材。";
            default -> null;
        };
    }

    private JsonNode parseError(String raw) {
        int start = raw.indexOf('{'), end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
            JsonNode error = root.path("error");
            if (error.isTextual()) {
                try { error = objectMapper.readTree(error.asText()); } catch (Exception ignored) { return root; }
            }
            return error.isMissingNode() || error.isNull() ? root : error;
        } catch (Exception ignored) { return null; }
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull() && value.isValueNode() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private String requestIdFrom(String raw) {
        Matcher matcher = REQUEST_ID.matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String concise(String text) {
        String clean = text.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() > 180 ? clean.substring(0, 180) + "…" : clean;
    }
}
