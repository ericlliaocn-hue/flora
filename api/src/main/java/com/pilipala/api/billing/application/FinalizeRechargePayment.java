package com.pilipala.api.billing.application;

import com.pilipala.api.billing.domain.RechargeOrder;
import com.pilipala.api.billing.domain.RechargeOrderStatus;
import com.pilipala.api.billing.domain.WalletLedgerEntry;
import com.pilipala.api.billing.infra.RechargeOrderRepository;
import com.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * notify 与查单补单共用的幂等入账。
 */
@Service
public class FinalizeRechargePayment {

    private final RechargeOrderRepository orders;
    private final WalletService wallets;

    public FinalizeRechargePayment(RechargeOrderRepository orders, WalletService wallets) {
        this.orders = orders;
        this.wallets = wallets;
    }

    /**
     * @return true 表示本次调用新完成入账；false 表示已 paid 的幂等重放
     */
    @Transactional
    public boolean applyPaid(String outTradeNo, String providerTradeNo, long moneyFen) {
        RechargeOrder order = orders.findByOutTradeNo(outTradeNo).orElseThrow(JobException::rechargeOrderNotFound);
        if (order.moneyFen() != moneyFen) {
            throw JobException.validation();
        }
        if (order.status() == RechargeOrderStatus.PAID) {
            return false;
        }
        if (order.status() != RechargeOrderStatus.PENDING_PAY) {
            throw JobException.validation();
        }
        if (!orders.claimPaidIfPending(outTradeNo, providerTradeNo)) {
            RechargeOrder latest = orders.findByOutTradeNo(outTradeNo).orElseThrow();
            if (latest.status() == RechargeOrderStatus.PAID) {
                return false;
            }
            throw JobException.validation();
        }
        RechargeOrder claimed = orders.findByOutTradeNo(outTradeNo).orElseThrow();
        WalletLedgerEntry ledger = wallets.credit(claimed.userId(), claimed.credits(), claimed.id());
        orders.attachLedger(outTradeNo, ledger.id());
        return true;
    }
}
