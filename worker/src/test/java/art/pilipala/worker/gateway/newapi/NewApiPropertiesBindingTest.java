package art.pilipala.worker.gateway.newapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 证明 {@code pilipala.gateway.newapi.*} 能通过 Spring 绑定到 record。 */
class NewApiPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsBaseUrlKeyAndTimeout() {
        runner.withPropertyValues(
                        "pilipala.gateway.newapi.base-url=https://api.example.com/",
                        "pilipala.gateway.newapi.api-key=test-key",
                        "pilipala.gateway.newapi.timeout-ms=15000")
                .run(context -> {
                    NewApiProperties properties = context.getBean(NewApiProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://api.example.com");
                    assertThat(properties.apiKey()).isEqualTo("test-key");
                    assertThat(properties.timeoutMs()).isEqualTo(15000L);
                    assertThat(properties.configured()).isTrue();
                    assertThat(properties.videosUrl()).isEqualTo("https://api.example.com/v1/videos");
                });
    }

    @Test
    void emptyBaseUrlIsNotConfigured() {
        runner.withPropertyValues("pilipala.gateway.newapi.base-url=", "pilipala.gateway.newapi.api-key=test-key")
                .run(context -> {
                    NewApiProperties properties = context.getBean(NewApiProperties.class);
                    assertThat(properties.baseUrl()).isNull();
                    assertThat(properties.configured()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NewApiProperties.class)
    static class PropertiesConfig {
    }
}
