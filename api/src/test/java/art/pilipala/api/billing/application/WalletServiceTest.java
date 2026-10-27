package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.JobTerminalState;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.billing.support.LedgerAssertions;
import art.pilipala.api.job.infra.JobException;
import org.junit.jupiter.api.Test;

/**
 * 预付费账本：充值、冻结、确认实扣、解冻退回，以及它们的不变量与幂等。
 */
class WalletServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final long COST = 10;

    private final InMemoryWalletStore store = new InMemoryWalletStore();
    private final InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
    private final CreditGrantService grantService = new CreditGrantService(grantStore, store, store);
    private final WalletService wallets = new WalletService(store, store, store, grantService);

    @Test
    void topUpCreditsTheWalletAndWritesATopupEntry() {
        wallets.credit(USER, 100);

        Wallet wallet = store.walletOf(USER);
        assertThat(wallet.available()).isEqualTo(100);
        assertThat(wallet.frozen()).isZero();

        List<WalletLedgerEntry> entries = store.findLedgerByUserId(USER);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(WalletLedgerType.TOPUP);
        assertThat(entries.get(0).amount()).isEqualTo(100);
        assertThat(entries.get(0).holdId()).isNull();
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void topUpRejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> wallets.credit(USER, 0))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
        assertThat(store.allLedger()).isEmpty();
    }

    @Test
    void freezeMovesAvailableToFrozenAndWritesAHold() {
        wallets.credit(USER, 100);

        WalletHold hold = wallets.freeze(USER, COST);

        Wallet wallet = store.walletOf(USER);
        assertThat(wallet.available()).isEqualTo(90);
        assertThat(wallet.frozen()).isEqualTo(COST);

        assertThat(store.allHolds()).hasSize(1);
        assertThat(hold.status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(hold.amount()).isEqualTo(COST);
        assertThat(hold.jobId()).isNull();
        assertThat(hold.finalizedAt()).isNull();

        List<WalletLedgerEntry> entries = store.findLedgerByUserId(USER);
        assertThat(entries).hasSize(2);
        WalletLedgerEntry freeze = entries.get(1);
        assertThat(freeze.type()).isEqualTo(WalletLedgerType.FREEZE);
        assertThat(freeze.amount()).isEqualTo(COST);
        assertThat(freeze.holdId()).isEqualTo(hold.id());
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void freezeFailsWhenTheBalanceIsNotEnough() {
        assertThatThrownBy(() -> wallets.freeze(USER, COST))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("WALLET_INSUFFICIENT"));

        assertThat(store.allHolds()).isEmpty();
        assertThat(store.allLedger()).isEmpty();
        assertThat(store.walletOf(USER).available()).isZero();
        assertThat(store.walletOf(USER).frozen()).isZero();
    }

    @Test
    void bindJobLinksTheHoldAndItsFreezeEntry() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);
        UUID jobId = UUID.randomUUID();

        wallets.bindJob(hold.id(), jobId);

        assertThat(store.findHold(hold.id()).orElseThrow().jobId()).isEqualTo(jobId);
        assertThat(store.findLedgerByUserId(USER).get(1).jobId()).isEqualTo(jobId);
    }

    @Test
    void confirmConsumesFrozenAndKeepsAvailable() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);

        assertThat(wallets.confirm(hold.id())).isTrue();

        Wallet wallet = store.walletOf(USER);
        assertThat(wallet.available()).isEqualTo(90);
        assertThat(wallet.frozen()).isZero();

        WalletHold confirmed = store.findHold(hold.id()).orElseThrow();
        assertThat(confirmed.status()).isEqualTo(WalletHoldStatus.CONFIRMED);
        assertThat(confirmed.finalizedAt()).isNotNull();

        WalletLedgerEntry entry = store.findLedgerByUserId(USER).get(2);
        assertThat(entry.type()).isEqualTo(WalletLedgerType.CONFIRM);
        assertThat(entry.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void confirmIsIdempotent() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);
        wallets.confirm(hold.id());

        assertThat(wallets.confirm(hold.id())).isFalse();

        assertThat(store.walletOf(USER).frozen()).isZero();
        assertThat(store.walletOf(USER).available()).isEqualTo(90);
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void releaseReturnsTheFrozenAmountToAvailable() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);

        assertThat(wallets.release(hold.id())).isTrue();

        Wallet wallet = store.walletOf(USER);
        assertThat(wallet.available()).isEqualTo(100);
        assertThat(wallet.frozen()).isZero();
        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.RELEASED);

        WalletLedgerEntry entry = store.findLedgerByUserId(USER).get(2);
        assertThat(entry.type()).isEqualTo(WalletLedgerType.RELEASE);
        assertThat(entry.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void releaseIsIdempotent() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);
        wallets.release(hold.id());

        assertThat(wallets.release(hold.id())).isFalse();

        assertThat(store.walletOf(USER).available()).isEqualTo(100);
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void switchingToTheOppositeTerminalStateIsRejected() {
        wallets.credit(USER, 100);
        WalletHold hold = wallets.freeze(USER, COST);
        wallets.confirm(hold.id());

        assertThatThrownBy(() -> wallets.release(hold.id()))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));

        assertThat(store.walletOf(USER).available()).isEqualTo(90);
        assertThat(store.walletOf(USER).frozen()).isZero();
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, USER);
    }

    @Test
    void settlingByJobTerminalStateConfirmsOrReleases() {
        wallets.credit(USER, 100);
        WalletHold succeeded = wallets.freeze(USER, COST);
        WalletHold dead = wallets.freeze(USER, COST);

        wallets.settleForJob(succeeded.id(), JobTerminalState.SUCCEEDED);
        wallets.settleForJob(dead.id(), JobTerminalState.DEAD);

        Wallet wallet = store.walletOf(USER);
        assertThat(wallet.frozen()).isZero();
        assertThat(wallet.available()).isEqualTo(90);
        LedgerAssertions.assertInvariants(store, USER);
    }
}
