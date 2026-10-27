package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.CreditGrantRepository;
import art.pilipala.api.billing.infra.WalletLedgerRepository;
import art.pilipala.api.billing.infra.WalletRepository;
import art.pilipala.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditGrantService {

    private static final Logger log = LoggerFactory.getLogger(CreditGrantService.class);
    private static final int ADMIN_VALIDITY_DAYS = 730;

    private final CreditGrantRepository grants;
    private final WalletRepository wallets;
    private final WalletLedgerRepository ledger;

    public CreditGrantService(CreditGrantRepository grants, WalletRepository wallets, WalletLedgerRepository ledger) {
        this.grants = grants;
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Transactional
    public void grantForAdmin(UUID userId, UUID walletId, long amount) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        Instant expires = Instant.now().plus(ADMIN_VALIDITY_DAYS, ChronoUnit.DAYS);
        grants.insert(new CreditGrant(
                0L, userId, walletId, "admin", "admin-topup", "general", amount, amount, expires, null,
                Instant.now()));
    }

    @Transactional
    public long grantForRecharge(
            UUID userId,
            Wallet wallet,
            UUID rechargeOrderId,
            CreditProduct product,
            long baseCredits,
            long fixedBonus,
            long firstPurchaseBonus) {
        String sourceId = rechargeOrderId.toString();
        Instant expires = expiresAt(product);
        long total = 0;
        if (baseCredits > 0) {
            grants.insert(new CreditGrant(
                    0L, userId, wallet.id(), "recharge", sourceId, "general", baseCredits, baseCredits, expires,
                    product == null ? null : product.id(), Instant.now()));
            total += baseCredits;
        }
        long bonus = fixedBonus + firstPurchaseBonus;
        if (bonus > 0) {
            grants.insert(new CreditGrant(
                    0L, userId, wallet.id(), "bonus", sourceId, "bonus", bonus, bonus, expires,
                    product == null ? null : product.id(), Instant.now()));
            total += bonus;
        }
        return total;
    }

    @Transactional
    public long grantLegacyRecharge(UUID userId, Wallet wallet, UUID rechargeOrderId, long credits) {
        Instant expires = Instant.now().plus(730, ChronoUnit.DAYS);
        grants.insert(new CreditGrant(
                0L, userId, wallet.id(), "recharge", rechargeOrderId.toString(), "general", credits, credits,
                expires, null, Instant.now()));
        return credits;
    }

    @Transactional
    public void consumeFromGrants(UUID walletId, UUID userId, long amount) {
        if (amount <= 0) {
            return;
        }
        long remaining = amount;
        for (CreditGrant grant : grants.listConsumableForUpdate(walletId)) {
            if (remaining <= 0) {
                break;
            }
            long take = Math.min(remaining, grant.creditsRemaining());
            long left = grant.creditsRemaining() - take;
            grants.updateRemaining(grant.id(), left);
            remaining -= take;
        }
        if (remaining > 0) {
            log.warn("grant 批次不足，wallet={} user={} shortfall={}", walletId, userId, remaining);
        }
    }

    @Transactional
    public WalletLedgerEntry expireGrant(CreditGrant grant) {
        long expired = grant.creditsRemaining();
        if (expired <= 0) {
            grants.updateRemaining(grant.id(), 0);
            return null;
        }
        if (!wallets.deductAvailable(grant.walletId(), expired)) {
            log.warn("过期扣减 available 失败 grant={} wallet={}", grant.id(), grant.walletId());
        }
        grants.updateRemaining(grant.id(), 0);
        return ledger.insert(
                grant.walletId(),
                grant.userId(),
                WalletLedgerType.EXPIRE,
                -expired,
                null,
                null,
                null);
    }

    private static Instant expiresAt(CreditProduct product) {
        if (product == null || product.validityDays() == null) {
            return null;
        }
        return Instant.now().plus(product.validityDays(), ChronoUnit.DAYS);
    }
}
