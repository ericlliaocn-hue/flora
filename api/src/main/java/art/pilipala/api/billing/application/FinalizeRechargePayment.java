package art.pilipala.api.billing.application;

import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * notify 与查单补单共用的幂等入账。
 */
@Service
public class FinalizeRechargePayment {

    private final RechargeOrderRepository orders;
    private final SettleRechargePayment settle;

    public FinalizeRechargePayment(RechargeOrderRepository orders, SettleRechargePayment settle) {
        this.orders = orders;
        this.settle = settle;
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
        WalletLedgerEntry ledger = settle.creditOrder(claimed);
        orders.attachLedger(outTradeNo, ledger.id());
        return true;
    }
}
