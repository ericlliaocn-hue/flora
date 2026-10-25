package art.pilipala.api.job.application;

import java.util.UUID;

import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.job.domain.JobStatus;
import org.springframework.stereotype.Service;

/**
 * 冻结单的结算触发点。
 *
 * <p>worker 只改 job.status，不碰钱包（ADR 0004）：任务终态一律由 api 侧把冻结单结掉。
 * succeeded 确认实扣，cancelled / dead 解冻退回；非终态不动（重试期间冻结保持原样）。
 *
 * <p>重放完全幂等：{@link WalletService#confirm} / {@link WalletService#release}
 * 对已终结的冻结单是静默返回。
 */
@Service
public class HoldSettlement {

    private final WalletService wallets;

    public HoldSettlement(WalletService wallets) {
        this.wallets = wallets;
    }

    /**
     * 按任务状态结算冻结单。没有冻结单、或任务还没进终态时什么都不做。
     *
     * @param holdId 冻结单 id；可为 null
     * @param status 任务当前状态
     */
    public void settle(UUID holdId, JobStatus status) {
        if (holdId == null) {
            return;
        }
        switch (status) {
            case SUCCEEDED -> wallets.confirm(holdId);
            case CANCELLED, DEAD -> wallets.release(holdId);
            default -> {
                // 非终态（重试中）保持冻结：既不重冻也不提前释放。
            }
        }
    }
}
