package com.flora.worker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 证明 {@link AnytokenProperties} 在真实 Spring 绑定路径下选中的是 6 参数主构造器：
 * 记录类有两个构造器时，光靠"唯一构造器"是选不出来的，必须靠 {@code @ConstructorBinding}。
 */
class AnytokenPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(GatewayPropertiesConfig.class);

    @Test
    void bindsTheVideoApiKeyFromTheGatewayPrefix() {
        runner.withPropertyValues(
                        "flora.gateway.mode=http",
                        "flora.gateway.base-url=https://api.anytoken.work/v1",
                        "flora.gateway.api-key=image-key",
                        "flora.gateway.video-api-key=video-key",
                        "flora.gateway.timeout-ms=15000",
                        "flora.gateway.generate-path=/v1/images/generations")
                .run(context -> {
                    AnytokenProperties properties = context.getBean(AnytokenProperties.class);
                    assertThat(properties.mode()).isEqualTo("http");
                    assertThat(properties.baseUrl()).isEqualTo("https://api.anytoken.work/v1");
                    assertThat(properties.apiKey()).isEqualTo("image-key");
                    assertThat(properties.videoApiKey()).isEqualTo("video-key");
                    assertThat(properties.timeoutMs()).isEqualTo(15000L);
                    assertThat(properties.generatePath()).isEqualTo("/v1/images/generations");
                });
    }

    @Test
    void keepsTheVideoApiKeyEmptyWhenItIsNotConfigured() {
        runner.withPropertyValues("flora.gateway.mode=stub", "flora.gateway.api-key=image-key")
                .run(context -> {
                    AnytokenProperties properties = context.getBean(AnytokenProperties.class);
                    assertThat(properties.videoApiKey()).isNull();
                    assertThat(properties.apiKeyForCapability("video.generate")).isEqualTo("image-key");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnytokenProperties.class)
    static class GatewayPropertiesConfig {}
}
