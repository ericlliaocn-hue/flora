package com.flora.api.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flora.public-base-url}：拼 notify/return 等对外 URL，无尾斜杠。
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
