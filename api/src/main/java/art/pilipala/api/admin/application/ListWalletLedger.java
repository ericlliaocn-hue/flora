package art.pilipala.api.admin.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.infra.WalletLedgerRepository;
import org.springframework.stereotype.Service;

/**
 * 用例：查某个用户的账本流水（运维排查用，按时间倒序）。
 */
@Service
public class ListWalletLedger {

    private final WalletLedgerRepository ledger;

    public ListWalletLedger(WalletLedgerRepository ledger) {
        this.ledger = ledger;
    }

    /**
     * @param userId 目标用户
     * @param limit 单页上限，内部收敛到 1..200
     */
    public List<AdminLedgerEntryView> list(UUID userId, int limit) {
        return ledger.findRecentByUserId(userId, AdminLimits.limit(limit)).stream()
                .map(entry -> new AdminLedgerEntryView(entry.id(), entry.userId(), entry.type().value(),
                        entry.amount(), entry.holdId(), entry.jobId(), entry.createdAt()))
                .toList();
    }
}
