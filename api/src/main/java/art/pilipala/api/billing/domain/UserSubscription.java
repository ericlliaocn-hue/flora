package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

public record UserSubscription(
        long id,
        UUID userId,
        long planId,
        String planCode,
        long priceId,
        String cycle,
        String variantCode,
        String status,
        Instant startedAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        int seats,
        long creditsPerMonth,
        Instant lastGrantAt,
        UUID sourceOrderId,
        Instant createdAt,
        Instant updatedAt) {
}
