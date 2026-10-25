package com.flora.api.billing.infra;

public final class ZpayUrlNormalizer {

    private ZpayUrlNormalizer() {
    }

    public static String normalize(String url, String apiBase) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("alipays://") || trimmed.startsWith("weixin://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            String base = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
            return base + trimmed;
        }
        return trimmed;
    }
}
