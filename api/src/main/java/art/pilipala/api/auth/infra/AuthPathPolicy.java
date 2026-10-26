package art.pilipala.api.auth.infra;

/**
 * 鉴权闸门路径规则（WP17 扩展公开 OTP / 重置密码）。
 */
public final class AuthPathPolicy {

    public static final String PROTECTED_PREFIX = "/api/v1/";
    public static final String REGISTER_PATH = "/api/v1/auth/register";
    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String OTP_SEND_PATH = "/api/v1/auth/otp/send";
    public static final String PASSWORD_RESET_PATH = "/api/v1/auth/password/reset";
    public static final String AUTH_FEATURES_PATH = "/api/v1/auth/features";
    /** Studio 顶栏 / 活动条公开配置，免登录。 */
    public static final String STUDIO_FEATURES_PATH = "/api/v1/studio/features";
    /** WP16：z-pay 异步通知（验签入账，非登录）。 */
    public static final String ZPAY_RECHARGE_WEBHOOK_PATH = "/api/v1/billing/recharge/webhook/zpay";

    private AuthPathPolicy() {
    }

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
                && !OTP_SEND_PATH.equals(normalized)
                && !PASSWORD_RESET_PATH.equals(normalized)
                && !AUTH_FEATURES_PATH.equals(normalized)
                && !STUDIO_FEATURES_PATH.equals(normalized)
                && !ZPAY_RECHARGE_WEBHOOK_PATH.equals(normalized);
    }
}
