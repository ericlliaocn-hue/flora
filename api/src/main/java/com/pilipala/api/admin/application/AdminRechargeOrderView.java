package com.pilipala.api.admin.application;

import java.time.Instant;

import com.pilipala.api.billing.domain.RechargeOrder;

public record AdminRechargeOrderView(
        String id,
        String userId,
        String outTradeNo,
        String status,
        long moneyFen,
        long credits,
        String packageId,
        String providerTradeNo,
        Instant paidAt,
        Instant createdAt) {

    public static AdminRechargeOrderView of(RechargeOrder order) {
        return new AdminRechargeOrderView(
                order.id().toString(),
                order.userId().toString(),
                order.outTradeNo(),
                order.status().value(),
                order.moneyFen(),
                order.credits(),
                order.packageId(),
                order.providerTradeNo(),
                order.paidAt(),
                order.createdAt());
    }
}
