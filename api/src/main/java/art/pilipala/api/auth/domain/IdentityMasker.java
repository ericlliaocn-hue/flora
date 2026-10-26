package art.pilipala.api.auth.domain;

/**
 * C 端脱敏展示规则（WP17）。
 */
public final class IdentityMasker {

    private IdentityMasker() {
    }

    public static String maskPhone(String e164) {
        if (e164 == null || e164.length() < 8) {
            return null;
        }
        String suffix = e164.substring(e164.length() - 4);
        if (e164.startsWith("+86")) {
            return "+86****" + suffix;
        }
        return "****" + suffix;
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String prefix = local.length() <= 2 ? local : local.substring(0, 2);
        return prefix + "***" + domain;
    }
}
