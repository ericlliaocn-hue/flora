package art.pilipala.api.auth.domain;

import java.util.Locale;
import java.util.Set;

/**
 * OTP 用途枚举，与 Redis key 中的 purpose 段一致。
 */
public enum OtpPurpose {
    REGISTER,
    LOGIN,
    REBIND_PHONE,
    REBIND_EMAIL,
    RESET_PASSWORD;

    private static final Set<String> REBIND = Set.of("rebind_phone", "rebind_email");

    public String redisValue() {
        return name().toLowerCase();
    }

    public static OtpPurpose parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (OtpPurpose purpose : values()) {
            if (purpose.redisValue().equals(normalized)) {
                return purpose;
            }
        }
        return null;
    }

    public boolean requiresLogin() {
        return REBIND.contains(redisValue());
    }
}
