package com.pilipala.api.auth.infra;

/**
 * 鉴权闸门的路径规则，全仓唯一来源。
 *
 * <p>受保护范围是整个 {@code /api/v1/**}，其中 {@code /api/v1/jobs/**}
 * （WP4 的 submit / query / result / cancel）由本规则一并覆盖，
 * WP4 不需要再单独登记。放行名单只有注册与登录两个入口。
 *
 * <p>{@code /actuator/**} 不在 {@code /api/v1/} 前缀下，天然在闸门外。
 */
public final class AuthPathPolicy {

    public static final String PROTECTED_PREFIX = "/api/v1/";
    public static final String REGISTER_PATH = "/api/v1/auth/register";
    public static final String LOGIN_PATH = "/api/v1/auth/login";
    /** WP16：z-pay 异步通知（验签入账，非登录）。 */
    public static final String ZPAY_RECHARGE_WEBHOOK_PATH = "/api/v1/billing/recharge/webhook/zpay";

    private AuthPathPolicy() {
    }

    /**
     * 该路径是否必须带有效会话。
     */
    public static boolean requiresLogin(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
        if (!normalized.startsWith(PROTECTED_PREFIX)) {
            return false;
        }
        return !REGISTER_PATH.equals(normalized)
                && !LOGIN_PATH.equals(normalized)
                && !ZPAY_RECHARGE_WEBHOOK_PATH.equals(normalized);
    }
}
