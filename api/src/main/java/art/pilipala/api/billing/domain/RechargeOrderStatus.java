package art.pilipala.api.billing.domain;

import java.util.Locale;

public enum RechargeOrderStatus {

    CREATED,
    PENDING_PAY,
    PAID,
    FAILED,
    CLOSED,
    REFUNDED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RechargeOrderStatus of(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
