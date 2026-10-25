package com.flora.api.billing.domain;

import java.util.Locale;

/**
 * 流水类型。取值与 {@code wallet_ledger.type} 的 CHECK 约束逐字一致：
 * {@code topup|freeze|confirm|release|refund}。
 *
 * <p>方向约定（见 data-model/billing.md）：
 * topup 可用 +amount；freeze 可用 -amount、已冻 +amount；
 * confirm 已冻 -amount；release 可用 +amount、已冻 -amount。
 */
public enum WalletLedgerType {

    TOPUP,
    FREEZE,
    CONFIRM,
    RELEASE,
    REFUND;

    /** 小写形式，写 DB 用它。 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WalletLedgerType of(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
