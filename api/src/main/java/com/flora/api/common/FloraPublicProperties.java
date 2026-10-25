package com.flora.api.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flora.public-base-url}：拼 notify/return 等对外 URL，无尾斜杠。生产经 env 设为
 * {@code https://pilipala.art}；本地联调可用 localhost，不在代码里写死。
 */
@ConfigurationProperties(prefix = "flora")
public record FloraPublicProperties(String publicBaseUrl) {

    public boolean isPublicBaseUrlConfigured() {
        return publicBaseUrl != null && !publicBaseUrl.isBlank();
    }

    public String normalizedPublicBaseUrl() {
        if (!isPublicBaseUrlConfigured()) {
            return "";
        }
        String trimmed = publicBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
