package art.pilipala.api.billing.infra;

import art.pilipala.api.billing.application.RechargeReconcileTick;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RechargeReconcileDispatcher {

    private final RechargeReconcileTick tick;

    public RechargeReconcileDispatcher(RechargeReconcileTick tick) {
        this.tick = tick;
    }

    @Scheduled(fixedDelayString = "${pilipala.billing.recharge.reconcile-poll-ms:5000}")
    public void dispatch() {
        tick.tick();
    }
}
