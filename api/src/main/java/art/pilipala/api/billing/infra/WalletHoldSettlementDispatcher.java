package art.pilipala.api.billing.infra;

import art.pilipala.api.billing.application.WalletHoldSettlementTick;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 冻结单结算的定时对账器：按配置的间隔调一次 {@link WalletHoldSettlementTick}。
 *
 * <p>worker 在 DB 里把 job 推到终态但没人结算冻结单时，靠它兜底。
 * 只负责「什么时候扫」，结算语义与幂等策略在用例里。
 */
@Component
public class WalletHoldSettlementDispatcher {

    private final WalletHoldSettlementTick tick;

    public WalletHoldSettlementDispatcher(WalletHoldSettlementTick tick) {
        this.tick = tick;
    }

    @Scheduled(fixedDelayString = "${pilipala.billing.settle-poll-ms:200}")
    public void dispatch() {
        tick.tick();
    }
}
