package art.pilipala.api.billing.domain;

import java.time.Instant;

public record MembershipPlan(
        long id,
        String code,
        String nameZh,
        String nameEn,
        int sortOrder,
        long creditsPerMonth,
        Integer concurrency,
        int storageGb,
        String audience,
        String benefitsJson,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
