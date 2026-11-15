package com.aigc.video.provider;

import com.aigc.video.ai.ModelCatalog;
import com.aigc.video.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 真实调用会产生费用，仅在显式设置 AIGC_LIVE_TEST=true 时运行 */
@SpringBootTest(classes = Seedance2LiveTest.Config.class)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "AIGC_LIVE_TEST", matches = "true")
class Seedance2LiveTest {

    @Autowired
    private ArkVideoProvider provider;

    @Autowired
    private ModelCatalog modelCatalog;

    @Autowired
    private AiProperties properties;

    @SpringBootConfiguration
    @EnableConfigurationProperties(AiProperties.class)
    @Import({ArkVideoProvider.class, ModelCatalog.class})
    static class Config {
    }

    @Test
    void submitAndPollSeedance2() {
        ModelCatalog.ResolvedModel model = modelCatalog.resolve(ModelCatalog.Capability.VIDEO,
                properties.getDefaults().getVideo());
        VideoContext context = new VideoContext(model.providerKey(), model.baseUrl(), model.apiKey(),
                model.modelId(), model.watermark());

        SubmitResult submitted = provider.submit(new SubmitCommand(
                "一只白色猫咪在窗边伸懒腰，柔和日光，固定镜头",
                "Seedance 2.0 接入验证",
                List.of(),
                "16:9",
                4,
                "480P",
                context));
        assertNotNull(submitted.providerTaskId());
        assertFalse(submitted.providerTaskId().isBlank());

        PollResult polled = provider.poll(submitted.providerTaskId(), context);
        assertNotNull(polled);
        assertNotEquals("FAILED", polled.status(), polled.error());
    }
}
