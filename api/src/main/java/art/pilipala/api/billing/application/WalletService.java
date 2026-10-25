package art.pilipala.api.billing.application;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.billing.domain.JobTerminalState;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.WalletHoldRepository;
import art.pilipala.api.billing.infra.WalletLedgerRepository;
import art.pilipala.api.billing.infra.WalletRepository;
import art.pilipala.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预付费钱包的账本服务：充值、冻结、确认实扣、解冻退回。
 *
 * <p>记账原则（ADR 0001 单账本）：
 * <ul>
 *   <li>每次余额或冻结额变动都写一条 {@code wallet_ledger} 流水，方向由 type 决定；</li>
 *   <li>{@code available >= 0} 与 {@code frozen >= 0} 由 SQL 的守卫条件兜住；</li>
 *   <li>终结冻结单靠条件更新 {@code WHERE id = ? AND status = 'frozen'}，终态只进一次，
 *       重放 confirm / release 完全幂等。</li>
 * </ul>
 *
 * <p>账本 100% 留在 api 进程：worker 不碰这些表（ADR 0004）。
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository wallets;
    private final WalletHoldRepository holds;
    private final WalletLedgerRepository ledger;

    public WalletService(WalletRepository wallets, WalletHoldRepository holds, WalletLedgerRepository ledger) {
        this.wallets = wallets;
        this.holds = holds;
        this.ledger = ledger;
    }

    /** 确保用户有钱包行；没有就惰性建一个 0 余额的。 */
    @Transactional
    public Wallet ensureWallet(UUID userId) {
        Optional<Wallet> existing = wallets.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Wallet created = wallets.insertIfAbsent(userId).orElse(null);
        if (created != null) {
            return created;
        }
        // 并发下另一个事务先建好了。
        return wallets.findByUserId(userId).orElseThrow(() -> new IllegalStateException("钱包初始化失败"));
    }

    /**
     * 充值：可用余额加 {@code amount}，同时写一条 {@code topup} 流水。
     *
     * <p>严禁「只改 wallet.available 不写流水」——那是流水与余额对不上的根源。
     */
    @Transactional
    public Wallet credit(UUID userId, long amount) {
        credit(userId, amount, null);
        return ensureWallet(userId);
    }

    /**
     * 充值：可用余额加 {@code amount}，同时写一条 {@code topup} 流水。
     *
     * @param rechargeOrderId 渠道充值订单 id（可选）
     */
    @Transactional
    public WalletLedgerEntry credit(UUID userId, long amount, UUID rechargeOrderId) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        Wallet wallet = ensureWallet(userId);
        wallets.addAvailable(wallet.id(), amount);
        return ledger.insert(wallet.id(), userId, WalletLedgerType.TOPUP, amount, null, null, rechargeOrderId);
    }

    /**
     * 充值退款扣回：可用减 {@code amount}，写 {@code refund} 流水。
     */
    @Transactional
    public WalletLedgerEntry refundDebit(UUID userId, long amount, UUID rechargeOrderId) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        Wallet wallet = ensureWallet(userId);
        if (!wallets.deductAvailable(wallet.id(), amount)) {
            throw JobException.rechargeNotRefundable();
        }
        return ledger.insert(wallet.id(), userId, WalletLedgerType.REFUND, amount, null, null, rechargeOrderId);
    }

    /**
     * 冻结：可用转已冻，落一张 {@code frozen} 冻结单与一条 {@code freeze} 流水。
     *
     * <p>余额不足时抛 {@code WALLET_INSUFFICIENT}，此时还没写任何冻结单与流水，
     * 调用方的事务会整体回滚，所以不会留下「冻了一半」的账。
     */
    @Transactional
    public WalletHold freeze(UUID userId, long amount) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        Wallet wallet = ensureWallet(userId);
        if (!wallets.reserve(wallet.id(), amount)) {
            throw JobException.walletInsufficient();
        }
        WalletHold hold = holds.insert(userId, null, amount);
        ledger.insert(wallet.id(), userId, WalletLedgerType.FREEZE, amount, hold.id(), null, null);
        return hold;
    }

    /** 回填冻结单与流水的关联任务（冻结时 job 还没落库，先空着）。 */
    @Transactional
    public void bindJob(UUID holdId, UUID jobId) {
        holds.bindJob(holdId, jobId);
        ledger.bindJobId(holdId, jobId);
    }

    /**
     * 确认实扣：冻结转已扣，写一条 {@code confirm} 流水。
     *
     * @return true 表示这次真的终结了冻结单；false 表示已经是 confirmed 的幂等重放
     */
    @Transactional
    public boolean confirm(UUID holdId) {
        return finalize(holdId, WalletHoldStatus.CONFIRMED);
    }

    /**
     * 解冻退回：冻结额全额退回可用，写一条 {@code release} 流水。
     *
     * @return true 表示这次真的终结了冻结单；false 表示已经是 released 的幂等重放
     */
    @Transactional
    public boolean release(UUID holdId) {
        return finalize(holdId, WalletHoldStatus.RELEASED);
    }

    /**
     * 按任务终态结算：succeeded 确认实扣，cancelled / dead 解冻退回。
     */
    @Transactional
    public void settleForJob(UUID holdId, JobTerminalState state) {
        if (state == JobTerminalState.SUCCEEDED) {
            confirm(holdId);
        } else {
            release(holdId);
        }
    }

    private boolean finalize(UUID holdId, WalletHoldStatus target) {
        Optional<WalletHold> updated = holds.finalizeIfFrozen(holdId, target);
        if (updated.isEmpty()) {
            WalletHold current = holds.findHold(holdId).orElseThrow(JobException::notFound);
            if (current.status() == target) {
                return false;
            }
            log.warn("冻结单终态互转被拒绝 hold={} current={} requested={}",
                    holdId, current.status().value(), target.value());
            throw JobException.validation();
        }
        WalletHold hold = updated.get();
        Wallet wallet = wallets.findByUserId(hold.userId())
                .orElseThrow(() -> new IllegalStateException("冻结单对应的钱包不存在"));
        if (target == WalletHoldStatus.CONFIRMED) {
            wallets.consumeFrozen(wallet.id(), hold.amount());
        } else {
            wallets.releaseFrozen(wallet.id(), hold.amount());
        }
        ledger.insert(wallet.id(), hold.userId(),
                target == WalletHoldStatus.CONFIRMED ? WalletLedgerType.CONFIRM : WalletLedgerType.RELEASE,
                hold.amount(), hold.id(), hold.jobId(), null);
        return true;
    }
}
