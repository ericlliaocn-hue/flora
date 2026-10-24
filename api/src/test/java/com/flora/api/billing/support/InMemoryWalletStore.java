package com.flora.api.billing.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.flora.api.billing.domain.JobTerminalState;
import com.flora.api.billing.domain.PendingSettlement;
import com.flora.api.billing.domain.Wallet;
import com.flora.api.billing.domain.WalletHold;
import com.flora.api.billing.domain.WalletHoldStatus;
import com.flora.api.billing.domain.WalletLedgerEntry;
import com.flora.api.billing.domain.WalletLedgerType;
import com.flora.api.billing.infra.WalletHoldRepository;
import com.flora.api.billing.infra.WalletLedgerRepository;
import com.flora.api.billing.infra.WalletRepository;

/**
 * 内存版钱包仓储：账本测试不需要 Postgres。
 *
 * <p>行为对齐 {@code Jdbc*}Repository 的语义（一用户一钱包、余额守卫、
 * 冻结单终态只进一次），只是把数据放在内存里。三个仓储接口一起实现，
 * 方便用例直接拿到同一份数据。
 *
 * <p>钱包要知道任务终态才能算出「待结算」的冻结单，所以构造时可以传一个
 * 任务状态查询函数（测试里接 {@code InMemoryJobStore}）。
 */
public class InMemoryWalletStore implements WalletRepository, WalletHoldRepository, WalletLedgerRepository {

    private final Map<UUID, Wallet> wallets = new LinkedHashMap<>();
    private final Map<UUID, WalletHold> holds = new LinkedHashMap<>();
    private final List<WalletLedgerEntry> ledger = new ArrayList<>();
    private final Function<UUID, String> jobStatusLookup;

    public InMemoryWalletStore() {
        this(null);
    }

    public InMemoryWalletStore(Function<UUID, String> jobStatusLookup) {
        this.jobStatusLookup = jobStatusLookup;
    }

    // ----- wallet -----

    @Override
    public Optional<Wallet> findWallet(UUID walletId) {
        return Optional.ofNullable(wallets.get(walletId));
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return wallets.values().stream().filter(wallet -> wallet.userId().equals(userId)).findFirst();
    }

    @Override
    public Optional<Wallet> insertIfAbsent(UUID userId) {
        if (findByUserId(userId).isPresent()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Wallet wallet = new Wallet(UUID.randomUUID(), userId, 0, 0, now);
        wallets.put(wallet.id(), wallet);
        return Optional.of(wallet);
    }

    @Override
    public boolean reserve(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.available() < amount) {
            return false;
        }
        wallets.put(walletId, new Wallet(wallet.id(), wallet.userId(), wallet.available() - amount,
                wallet.frozen() + amount, Instant.now()));
        return true;
    }

    @Override
    public boolean addAvailable(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null) {
            return false;
        }
        wallets.put(walletId, new Wallet(wallet.id(), wallet.userId(), wallet.available() + amount,
                wallet.frozen(), Instant.now()));
        return true;
    }

    @Override
    public boolean consumeFrozen(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.frozen() < amount) {
            return false;
        }
        wallets.put(walletId, new Wallet(wallet.id(), wallet.userId(), wallet.available(),
                wallet.frozen() - amount, Instant.now()));
        return true;
    }

    @Override
    public boolean releaseFrozen(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.frozen() < amount) {
            return false;
        }
        wallets.put(walletId, new Wallet(wallet.id(), wallet.userId(), wallet.available() + amount,
                wallet.frozen() - amount, Instant.now()));
        return true;
    }

    // ----- wallet_hold -----

    @Override
    public WalletHold insert(UUID userId, UUID jobId, long amount) {
        WalletHold hold = new WalletHold(UUID.randomUUID(), userId, jobId, amount, WalletHoldStatus.FROZEN,
                Instant.now(), null);
        holds.put(hold.id(), hold);
        return hold;
    }

    @Override
    public Optional<WalletHold> findHold(UUID holdId) {
        return Optional.ofNullable(holds.get(holdId));
    }

    @Override
    public Optional<WalletHold> finalizeIfFrozen(UUID holdId, WalletHoldStatus target) {
        WalletHold hold = holds.get(holdId);
        if (hold == null || hold.status() != WalletHoldStatus.FROZEN) {
            return Optional.empty();
        }
        WalletHold finalized = new WalletHold(hold.id(), hold.userId(), hold.jobId(), hold.amount(), target,
                hold.createdAt(), Instant.now());
        holds.put(holdId, finalized);
        return Optional.of(finalized);
    }

    @Override
    public boolean bindJob(UUID holdId, UUID jobId) {
        WalletHold hold = holds.get(holdId);
        if (hold == null || hold.jobId() != null) {
            return false;
        }
        holds.put(holdId, new WalletHold(hold.id(), hold.userId(), jobId, hold.amount(), hold.status(),
                hold.createdAt(), hold.finalizedAt()));
        return true;
    }

    @Override
    public List<PendingSettlement> findPendingSettlement(UUID userId, int limit) {
        return holds.values().stream()
                .filter(hold -> hold.status() == WalletHoldStatus.FROZEN)
                .filter(hold -> userId == null || hold.userId().equals(userId))
                .filter(hold -> hold.jobId() != null)
                .filter(hold -> jobState(hold.jobId()) != null)
                .sorted(Comparator.comparing(WalletHold::createdAt))
                .limit(limit)
                .map(hold -> new PendingSettlement(hold.id(), hold.userId(), hold.jobId(), hold.amount(),
                        jobState(hold.jobId())))
                .toList();
    }

    // ----- wallet_ledger -----

    @Override
    public WalletLedgerEntry insert(UUID walletId, UUID userId, WalletLedgerType type, long amount, UUID holdId,
            UUID jobId) {
        WalletLedgerEntry entry = new WalletLedgerEntry(UUID.randomUUID(), walletId, userId, type, amount, holdId,
                jobId, Instant.now());
        ledger.add(entry);
        return entry;
    }

    @Override
    public boolean bindJobId(UUID holdId, UUID jobId) {
        boolean updated = false;
        for (int i = 0; i < ledger.size(); i++) {
            WalletLedgerEntry entry = ledger.get(i);
            if (entry.holdId() != null && entry.holdId().equals(holdId) && entry.jobId() == null) {
                ledger.set(i, new WalletLedgerEntry(entry.id(), entry.walletId(), entry.userId(), entry.type(),
                        entry.amount(), entry.holdId(), jobId, entry.createdAt()));
                updated = true;
            }
        }
        return updated;
    }

    @Override
    public List<WalletLedgerEntry> findLedgerByUserId(UUID userId) {
        return ledger.stream().filter(entry -> entry.userId().equals(userId)).toList();
    }

    // ----- 测试辅助 -----

    /** 所有流水，按记账顺序。 */
    public List<WalletLedgerEntry> allLedger() {
        return List.copyOf(ledger);
    }

    /** 所有冻结单。 */
    public List<WalletHold> allHolds() {
        return List.copyOf(holds.values());
    }

    /** 某个用户的钱包快照。 */
    public Wallet walletOf(UUID userId) {
        return findByUserId(userId).orElseThrow();
    }

    private JobTerminalState jobState(UUID jobId) {
        if (jobStatusLookup == null) {
            return null;
        }
        return JobTerminalState.of(jobStatusLookup.apply(jobId));
    }
}
