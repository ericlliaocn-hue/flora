package com.flora.api.billing.infra;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ZpayMoney {

    private ZpayMoney() {
    }

    public static String fenToYuanString(long moneyFen) {
        return BigDecimal.valueOf(moneyFen, 2).toPlainString();
    }

    public static long yuanStringToFen(String money) {
        if (money == null || money.isBlank()) {
            throw new IllegalArgumentException("empty money");
        }
        BigDecimal yuan = new BigDecimal(money.trim());
        return yuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
}
