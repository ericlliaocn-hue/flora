package com.flora.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.flora.api.billing.domain.RechargeOrder;
import com.flora.api.billing.infra.RechargeOrderRepository;
import com.flora.api.billing.infra.RechargeProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RechargeReconcileTick {

    private final RechargeOrderRepository orders;
    private final RechargeProperties properties;
    private final ReconcileRechargeOrder reconcile;

    public RechargeReconcileTick(
            RechargeOrderRepository orders, RechargeProperties properties, ReconcileRechargeOrder reconcile) {
        this.orders = orders;
        this.properties = properties;
        this.reconcile = reconcile;
    }

    @Transactional
    public void tick() {
        Instant stale = Instant.now().minus(30, ChronoUnit.SECONDS);
        List<RechargeOrder> pending = orders.findPendingPayBefore(stale, properties.reconcileBatchSize());
        Instant ttlCutoff = Instant.now().minus(properties.orderTtlMinutes(), ChronoUnit.MINUTES);
        for (RechargeOrder order : pending) {
            if (order.createdAt().isBefore(ttlCutoff)) {
                orders.markClosed(order.id());
                continue;
            }
            reconcile.trySyncPaid(order.outTradeNo());
        }
    }
}
