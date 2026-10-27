package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditGrant;

public interface CreditGrantRepository {

    CreditGrant insert(CreditGrant draft);

    List<CreditGrant> listConsumableForUpdate(UUID walletId);

    void updateRemaining(long id, long remaining);

    List<CreditGrant> listByUser(UUID userId, int limit);

    List<CreditGrant> findExpiredForUpdate(Instant before, int limit);

    List<CreditGrant> listActiveSummary(UUID userId, int limit);

    List<CreditGrant> listSubscriptionRemainingForUpdate(UUID userId);
}
