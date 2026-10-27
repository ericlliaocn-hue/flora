package art.pilipala.api.billing.application;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.infra.RechargeOrderRepository;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class FirstPurchaseBonusPolicy {

    private final RechargeOrderRepository orders;

    public FirstPurchaseBonusPolicy(RechargeOrderRepository orders) {
        this.orders = orders;
    }

    public long compute(UUID userId, CreditProduct product, UUID currentOrderId, long baseCredits) {
        if (product == null || product.code() == null) {
            return 0;
        }
        if (orders.countPaidByUserAndProductCodeExcluding(userId, product.code(), currentOrderId) > 0) {
            return 0;
        }
        if (product.firstPurchaseBonusPercent() != null && product.firstPurchaseBonusPercent() > 0) {
            return baseCredits * product.firstPurchaseBonusPercent() / 100;
        }
        return product.firstPurchaseBonusCredits();
    }
}
