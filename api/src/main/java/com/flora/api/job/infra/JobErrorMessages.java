package com.flora.api.job.infra;

import java.util.Locale;
import java.util.Map;

import com.flora.api.common.ErrorCodes;

/**
 * Job 域错误短句：zh + en 静态 Map。
 *
 * <p>风格对齐 {@code AuthErrorMessages}。WP9 引入 {@code i18n_message} 表后删掉本类，
 * 改为按 locale 查表。
 */
public final class JobErrorMessages {

    private static final Map<String, Map<String, String>> MESSAGES = Map.of(
            ErrorCodes.JOB_NOT_FOUND, Map.of(
                    "zh", "任务不存在。",
                    "en", "The task does not exist."),
            ErrorCodes.JOB_CANCELLED, Map.of(
                    "zh", "任务已取消。",
                    "en", "The task has been cancelled."),
            ErrorCodes.IDEMPOTENCY_CONFLICT, Map.of(
                    "zh", "同一个幂等键对应了不同的请求内容。",
                    "en", "The same idempotency key was used with a different request."),
            ErrorCodes.JOB_FAILED, Map.of(
                    "zh", "任务执行失败。",
                    "en", "The task failed to run."),
            ErrorCodes.VALIDATION_ERROR, Map.of(
                    "zh", "请求参数不合法。",
                    "en", "The request parameters are invalid."),
            ErrorCodes.PROVIDER_UNAVAILABLE, Map.of(
                    "zh", "生成服务暂时不可用，请稍后重试。",
                    "en", "The generation service is temporarily unavailable. Please try again later."),
            ErrorCodes.UNAUTHORIZED, Map.of(
                    "zh", "请先登录后再试。",
                    "en", "Please sign in first."));

    private JobErrorMessages() {
    }

    /**
     * 按 locale（{@code zh} / {@code en}）解析短句；没有偏好时回退 zh。
     */
    public static String resolve(String errorCode, String locale) {
        Map<String, String> byLocale = MESSAGES.get(errorCode);
        if (byLocale == null) {
            return errorCode;
        }
        return byLocale.getOrDefault(locale, byLocale.get("zh"));
    }

    /**
     * 从 Accept-Language 头解析出 {@code zh} / {@code en}。
     */
    public static String localeOf(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "zh";
        }
        String primary = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return primary.startsWith("en") ? "en" : "zh";
    }
}
