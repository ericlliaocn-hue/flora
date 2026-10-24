package com.flora.worker.gateway;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * {@code flora.gateway.*} 配置。
 *
 * @param mode         stub（本地确定性假上游，默认）或 http（真实 Anytoken）
 * @param baseUrl      http 模式的上游根地址，例如 {@code https://api.anytoken.work/v1}
 * @param apiKey       http 模式的生图 Bearer key；只从环境变量注入，不入库、不进日志
 * @param videoApiKey  视频分组（如 Seedance）的 Bearer key；留空时回退 {@code apiKey}
 * @param timeoutMs    http 模式的超时（毫秒）
 * @param generatePath http 模式的生图路径，例如 {@code /v1/images/generations}
 */
@ConfigurationProperties(prefix = "flora.gateway")
public record AnytokenProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String videoApiKey,
        long timeoutMs,
        String generatePath) {

    /**
     * 主构造器：显式标注 {@link ConstructorBinding}，让 Spring 在存在多个构造器时
     * 明确选它（记录类的规范构造器）。
     */
    @ConstructorBinding
    public AnytokenProperties {
    }

    /** 兼容构造器：WP10 之前的 5 参数写法继续可用（测试与旧调用点零破坏）。 */
    public AnytokenProperties(String mode, String baseUrl, String apiKey, long timeoutMs, String generatePath) {
        this(mode, baseUrl, apiKey, "", timeoutMs, generatePath);
    }

    /**
     * 按能力选 Key：视频分组用独立 Key，其余（生图等）用通用 Key。
     *
     * @param capability 能力标识，如 {@code image.generate} / {@code video.generate}
     */
    public String apiKeyForCapability(String capability) {
        if (capability != null
                && capability.toLowerCase(Locale.ROOT).startsWith("video")
                && videoApiKey != null
                && !videoApiKey.isBlank()) {
            return videoApiKey;
        }
        return apiKey;
    }
}
