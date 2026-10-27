package art.pilipala.api.billing.domain;

import java.time.Instant;

public record MembershipPlanPrice(
        long id,
        long planId,
        String cycle,
        String variantCode,
        long creditsPerMonth,
        int priceFen,
        Integer listPriceFen,
        Integer renewPriceFen,
        String badgeZh,
        int giftMonths,
        int sortOrder,
        boolean enabled,
        String metaJson,
        Instant createdAt,
        Instant updatedAt) {
}
