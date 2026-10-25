package com.flora.api.billing.application;

import com.flora.api.billing.domain.RechargeOrder;
import com.flora.api.billing.domain.RechargeOrderStatus;
import com.flora.api.billing.infra.RechargeOrderRepository;
import com.flora.api.billing.infra.ZpayClient;
import com.flora.api.billing.infra.ZpayClient.OrderQueryResult;
import com.flora.api.billing.infra.ZpayMoney;
import com.flora.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReconcileRechargeOrder {

    private static final Logger log = LoggerFactory.getLogger(ReconcileRechargeOrder.class);

    private final RechargeOrderRepository orders;
    private final ZpayClient zpay;
    private final FinalizeRechargePayment finalizePayment;

    public ReconcileRechargeOrder(
            RechargeOrderRepository orders, ZpayClient zpay, FinalizeRechargePayment finalizePayment) {
        this.orders = orders;
        this.zpay = zpay;
        this.finalizePayment = finalizePayment;
    }

    public void trySyncPaid(String outTradeNo) {
        RechargeOrder order = orders.findByOutTradeNo(outTradeNo).orElse(null);
        if (order == null || order.status() != RechargeOrderStatus.PENDING_PAY) {
            return;
        }
        try {
            OrderQueryResult query = zpay.queryOrder(outTradeNo);
            if (!query.paid()) {
                return;
            }
            long moneyFen = order.moneyFen();
            if (query.moneyYuan() != null) {
                moneyFen = ZpayMoney.yuanStringToFen(query.moneyYuan());
            }
            finalizePayment.applyPaid(outTradeNo, query.tradeNo(), moneyFen);
        } catch (JobException e) {
            log.debug("recharge sync skipped out={} code={}", outTradeNo, e.errorCode());
        }
    }
}
