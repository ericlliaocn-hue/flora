package com.flora.api.auth.infra;

import java.util.Locale;
import java.util.Map;

import com.flora.api.common.ErrorCodes;

/**
 * 错误短句的临时实现：只有已登记的 {@code UNAUTHORIZED} / {@code VALIDATION_ERROR}，
 * zh + en 两种。
 *
 * <p>WP9 引入 {@code i18n_message} 表后删掉本类，改为按 locale 查表。
 */
public final class AuthErrorMessages {

    private static final Map<String, Map<String, String>> MESSAGES = Map.of(
            ErrorCodes.UNAUTHORIZED, Map.of(
                    "zh", "请先登录后再试。",
                    "en", "Please sign in first."),
            ErrorCodes.VALIDATION_ERROR, Map.of(
                    "zh", "请求参数不合法。",
                    "en", "The request parameters are invalid."));

    private AuthErrorMessages() {
    }

    /**
     * 按请求语言解析短句；没有偏好时回退 zh。
     */
    public static String resolve(String errorCode, String acceptLanguage) {
        Map<String, String> byLocale = MESSAGES.get(errorCode);
        if (byLocale == null) {
            return errorCode;
        }
        return byLocale.getOrDefault(pickLanguage(acceptLanguage), byLocale.get("zh"));
    }

    private static String pickLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "zh";
        }
        String primary = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (primary.startsWith("en")) {
            return "en";
        }
        return "zh";
    }
}
