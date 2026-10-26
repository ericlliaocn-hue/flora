package art.pilipala.api.auth.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 手机 / 邮箱入参规范化。WP17 仅支持中国大陆手机号。
 */
public final class IdentityNormalizer {

    private static final Pattern CN_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private IdentityNormalizer() {
    }

    /**
     * 将手机原文规范为 E.164（+86 + 11 位）。无法识别时返回 null。
     */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("[\\s-]", "");
        if (digits.startsWith("+86")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        if (!CN_MOBILE.matcher(digits).matches()) {
            return null;
        }
        return "+86" + digits;
    }

    /**
     * 邮箱 trim + 小写；格式不合法时返回 null。
     */
    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            return null;
        }
        return email;
    }
}
