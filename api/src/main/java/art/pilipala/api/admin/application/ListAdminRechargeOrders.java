package art.pilipala.api.admin.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

@Service
public class ListAdminRechargeOrders {

    private final RechargeOrderRepository orders;

    public ListAdminRechargeOrders(RechargeOrderRepository orders) {
        this.orders = orders;
    }

    public List<AdminRechargeOrderView> list(String userId, String status, String outTradeNo, int limit) {
        UUID uid = null;
        if (userId != null && !userId.isBlank()) {
            try {
                uid = UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                throw JobException.validation();
            }
        }
        int capped = AdminLimits.limit(limit);
        return orders.findAdmin(uid, status, outTradeNo, capped).stream()
                .map(AdminRechargeOrderView::of)
                .toList();
    }
}
