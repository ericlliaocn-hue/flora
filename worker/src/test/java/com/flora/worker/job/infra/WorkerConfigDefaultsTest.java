package com.flora.worker.job.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.flora.worker.gateway.AnytokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * worker 的 {@code application.yml} 默认值必须与代码默认值一致。
 *
 * <p>视频轮询预算与 Redis 租约 TTL 都是会改变实际行为的边界值：yml 写一个数、
 * 代码兜底写另一个数，行为就会随部署方式漂移。这里把两边钉死。
 */
class WorkerConfigDefaultsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(JobConfig.class);

    /** 租约 TTL 要盖住 600s 的视频轮询窗口，否则视频生成中途锁就过期了。 */
    @Test
    void theYamlLeaseTtlCoversTheVideoPollWindow() {
        runner.run(context -> {
            WorkerJobProperties properties = context.getBean(WorkerJobProperties.class);
            assertThat(properties.leaseTtlSeconds()).isEqualTo(660L);
        });
    }

    /** yml 里的视频轮询预算与代码常量必须是同一组数。 */
    @Test
    void theYamlVideoPollBudgetMatchesTheCodeDefaults() {
        runner.run(context -> {
            AnytokenProperties properties = context.getBean(AnytokenProperties.class);
            assertThat(properties.videoPollIntervalMs()).isEqualTo(AnytokenProperties.DEFAULT_VIDEO_POLL_INTERVAL_MS);
            assertThat(properties.videoMaxPollAttempts()).isEqualTo(AnytokenProperties.DEFAULT_VIDEO_MAX_POLL_ATTEMPTS);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ WorkerJobProperties.class, AnytokenProperties.class })
    static class JobConfig {}
}
