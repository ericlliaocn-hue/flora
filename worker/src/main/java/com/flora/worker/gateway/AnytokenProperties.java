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
 * @param videoGeneratePath Seedance 视频提交路径，例如 {@code /v1/videos/generations}
 * @param videoStatusPathPrefix Seedance 视频状态与内容路径前缀，例如 {@code /v1/videos}
 * @param videoPollIntervalMs 视频状态轮询间隔（毫秒）
 * @param videoMaxPollAttempts 视频状态轮询次数上限
 */
@ConfigurationProperties(prefix = "flora.gateway")
public record AnytokenProperties(
        String mode,
        String baseUrl,
        String apiKey,
        String videoApiKey,
        long timeoutMs,
        String generatePath,
        String videoGeneratePath,
        String videoStatusPathPrefix,
        long videoPollIntervalMs,
        int videoMaxPollAttempts) {

    /** 视频提交路径的默认值。 */
    public static final String DEFAULT_VIDEO_GENERATE_PATH = "/v1/videos/generations";
    /** 视频状态 / 内容路径前缀的默认值。 */
    public static final String DEFAULT_VIDEO_STATUS_PATH_PREFIX = "/v1/videos";
    /**
     * 视频轮询间隔的默认值（毫秒）。
     *
     * <p>对齐上游响应里的 {@code poll_after_ms: 5000}：上游建议 5 秒轮询一次，
     * 轮询太密只是多打请求，不会更快出片。
     */
    public static final long DEFAULT_VIDEO_POLL_INTERVAL_MS = 5000L;
    /** 视频轮询次数上限的默认值：{@code 5000ms × 120 次 = 600s} 轮询窗口。 */
    public static final int DEFAULT_VIDEO_MAX_POLL_ATTEMPTS = 120;

    /**
     * 主构造器：显式标注 {@link ConstructorBinding}，让 Spring 在存在多个构造器时
     * 明确选它（记录类的规范构造器）。
     *
     * <p>视频相关的四个字段在这里兜底：直接 new 出来的旧调用点不会拿到 null。
     */
    @ConstructorBinding
    public AnytokenProperties {
        videoGeneratePath = defaultText(videoGeneratePath, DEFAULT_VIDEO_GENERATE_PATH);
        videoStatusPathPrefix = defaultText(videoStatusPathPrefix, DEFAULT_VIDEO_STATUS_PATH_PREFIX);
        videoPollIntervalMs = videoPollIntervalMs <= 0 ? DEFAULT_VIDEO_POLL_INTERVAL_MS : videoPollIntervalMs;
        videoMaxPollAttempts = videoMaxPollAttempts <= 0 ? DEFAULT_VIDEO_MAX_POLL_ATTEMPTS : videoMaxPollAttempts;
    }

    /** 兼容构造器：WP10 之前的 5 参数写法继续可用（测试与旧调用点零破坏）。 */
    public AnytokenProperties(String mode, String baseUrl, String apiKey, long timeoutMs, String generatePath) {
        this(mode, baseUrl, apiKey, "", timeoutMs, generatePath);
    }

    /** 兼容构造器：WP10 的 6 参数写法继续可用（视频 key 已有的调用点零破坏）。 */
    public AnytokenProperties(String mode, String baseUrl, String apiKey, String videoApiKey, long timeoutMs,
            String generatePath) {
        this(mode, baseUrl, apiKey, videoApiKey, timeoutMs, generatePath, null, null, 0, 0);
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

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
