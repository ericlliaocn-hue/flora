package art.pilipala.worker.gateway.newapi;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NewAPI 视频网关配置（{@code pilipala.gateway.newapi.*}）。
 *
 * <p>Key 只从 env 注入，不入库、不进日志。base-url 为空或 key 为空时视为未配置，
 * 客户端直接返回不可重试的 {@code PROVIDER_UNAVAILABLE}，不会发请求。
 */
@ConfigurationProperties(prefix = "pilipala.gateway.newapi")
public record NewApiProperties(String baseUrl, String apiKey, long timeoutMs) {

    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    public NewApiProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        timeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public String videosUrl() {
        return baseUrl + "/v1/videos";
    }

    public String taskUrl(String taskId) {
        String encoded = URLEncoder.encode(taskId, StandardCharsets.UTF_8).replace("+", "%20");
        return videosUrl() + "/" + encoded;
    }

    public String contentUrl(String taskId) {
        return taskUrl(taskId) + "/content";
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }
}
