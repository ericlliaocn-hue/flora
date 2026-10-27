package art.pilipala.api.billing.domain;

import java.time.Instant;

/**
 * 积分超市 SKU（库表 {@code credit_product}）。
 */
public record CreditProduct(
        long id,
        String code,
        String tab,
        String nameZh,
        String nameEn,
        String descriptionZh,
        String descriptionEn,
        int priceFen,
        Integer listPriceFen,
        long credits,
        long bonusCredits,
        long firstPurchaseBonusCredits,
        Integer firstPurchaseBonusPercent,
        Integer validityDays,
        String minPlanCode,
        String modelScope,
        int sortOrder,
        boolean enabled,
        String badgeZh,
        String badgeEn,
        String metaJson,
        Instant createdAt,
        Instant updatedAt) {
}
