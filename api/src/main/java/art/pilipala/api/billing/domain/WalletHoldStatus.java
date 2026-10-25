package art.pilipala.api.billing.domain;

import java.util.Locale;

/**
 * 冻结单状态。取值与 {@code wallet_hold.status} 的 CHECK 约束逐字一致：
 * {@code frozen|confirmed|released}。
 */
public enum WalletHoldStatus {

    FROZEN,
    CONFIRMED,
    RELEASED;

    /** 小写形式，写 DB 用它。 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WalletHoldStatus of(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
