package art.pilipala.api.billing.domain;

import java.util.Locale;

public enum RechargeOrderKind {
    RECHARGE,
    MEMBERSHIP;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RechargeOrderKind of(String raw) {
        if (raw == null || raw.isBlank()) {
            return RECHARGE;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
