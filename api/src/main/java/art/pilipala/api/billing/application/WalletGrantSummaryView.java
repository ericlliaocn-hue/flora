package art.pilipala.api.billing.application;

import java.time.Instant;

import art.pilipala.api.billing.domain.CreditGrant;

public record WalletGrantSummaryView(long remaining, Instant expiresAt, String grantType) {

    public static WalletGrantSummaryView of(CreditGrant grant) {
        return new WalletGrantSummaryView(grant.creditsRemaining(), grant.expiresAt(), grant.grantType());
    }
}
