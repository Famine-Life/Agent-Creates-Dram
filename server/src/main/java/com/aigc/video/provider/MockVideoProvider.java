package com.aigc.video.provider;

import com.aigc.video.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示协议：无需任何密钥，按配置的秒数推进度，结束后返回一段演示视频。
 * 任务 ID 里编码了提交时间，因此无需额外存储即可算出进度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockVideoProvider implements VideoProvider {

    private static final String PREFIX = "mock-";
    private static final String DEFAULT_VIDEO_URL =
            "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4";

    private final AiProperties properties;

    @Override
    public String name() {
        return "mock-video";
    }

    @Override
    public SubmitResult submit(SubmitCommand command) {
        log.info("[mock-video] 提交生成任务 title={} 参考图={}张", command.title(), command.imageUrls().size());
        return new SubmitResult(PREFIX + System.currentTimeMillis());
    }

    @Override
    public PollResult poll(String providerTaskId, VideoContext context) {
        long startAt;
        try {
            startAt = Long.parseLong(providerTaskId.substring(PREFIX.length()));
        } catch (Exception e) {
            return PollResult.failed("非法的 mock 任务号：" + providerTaskId);
        }
        AiProperties.Provider mock = properties.getProviders() == null
                ? null : properties.getProviders().get("mock");
        int total = Math.max(1, mock == null || mock.getVideoSeconds() <= 0 ? 8 : mock.getVideoSeconds());
        String videoUrl = mock == null || mock.getVideoUrl() == null || mock.getVideoUrl().isBlank()
                ? DEFAULT_VIDEO_URL : mock.getVideoUrl();
        long elapsed = (System.currentTimeMillis() - startAt) / 1000;
        if (elapsed >= total) {
            return PollResult.succeeded(videoUrl, null);
        }
        int progress = (int) Math.max(5, Math.min(99, elapsed * 100 / total));
        return PollResult.running(progress);
    }
}
