package com.flora.api.job.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * api 的 {@code application.yml} 默认值与 worker 侧保持一致。
 *
 * <p>租约 TTL 要盖住 600s 的视频轮询窗口：视频生成要跑 3~10 分钟，
 * 租约比它短就会中途过期。
 */
class JobConfigDefaultsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(JobConfig.class);

    @Test
    void theYamlLeaseTtlCoversTheVideoPollWindow() {
        runner.run(context -> {
            JobTransportProperties properties = context.getBean(JobTransportProperties.class);
            assertThat(properties.leaseTtlSeconds()).isEqualTo(660L);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JobTransportProperties.class)
    static class JobConfig {}
}
