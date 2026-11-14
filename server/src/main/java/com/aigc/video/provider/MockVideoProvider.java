package com.aigc.video.provider;

import com.aigc.video.config.ProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示供应商：无需任何密钥，按配置的秒数推进度，结束后返回一段演示视频。
 * 任务 ID 里编码了提交时间，因此无需额外存储即可算出进度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockVideoProvider implements VideoProvider {

    private static final String PREFIX = "mock-";

    private final ProviderProperties properties;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public SubmitResult submit(SubmitCommand command) {
        log.info("[mock] 提交生成任务 title={} 参考图={}张", command.title(), command.imageUrls().size());
        return new SubmitResult(PREFIX + System.currentTimeMillis());
    }

    @Override
    public PollResult poll(String providerTaskId) {
        long startAt;
        try {
            startAt = Long.parseLong(providerTaskId.substring(PREFIX.length()));
        } catch (Exception e) {
            return PollResult.failed("非法的 mock 任务号：" + providerTaskId);
        }
        int total = Math.max(1, properties.getMock().getSeconds());
        long elapsed = (System.currentTimeMillis() - startAt) / 1000;
        if (elapsed >= total) {
            return PollResult.succeeded(properties.getMock().getVideoUrl(), null);
        }
        int progress = (int) Math.max(5, Math.min(99, elapsed * 100 / total));
        return PollResult.running(progress);
    }
}
