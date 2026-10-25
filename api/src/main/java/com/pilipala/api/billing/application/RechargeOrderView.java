package com.pilipala.api.billing.application;

import java.time.Instant;

import com.pilipala.api.billing.domain.RechargeOrder;

public record RechargeOrderView(
        String id,
        String outTradeNo,
        String status,
        long moneyFen,
        long credits,
        String payUrl,
        String qrcodeUrl,
        Instant expireAt,
        Instant paidAt) {

    public static RechargeOrderView of(RechargeOrder order, Instant expireAt) {
        String pay = order.payUrl();
        return new RechargeOrderView(
                order.id().toString(),
                order.outTradeNo(),
                order.status().value(),
                order.moneyFen(),
                order.credits(),
                pay,
                order.qrcodeUrl(),
                expireAt,
                order.paidAt());
    }

    public static RechargeOrderView ofStatusOnly(RechargeOrder order) {
        return new RechargeOrderView(
                order.id().toString(),
                order.outTradeNo(),
                order.status().value(),
                order.moneyFen(),
                order.credits(),
                order.payUrl(),
                order.qrcodeUrl(),
                null,
                order.paidAt());
    }
}
