package art.pilipala.api.admin.application;

import art.pilipala.api.billing.domain.CreditProduct;

public record AdminCreditProductView(
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
        String metaJson) {

    public static AdminCreditProductView of(CreditProduct product) {
        return new AdminCreditProductView(
                product.id(),
                product.code(),
                product.tab(),
                product.nameZh(),
                product.nameEn(),
                product.descriptionZh(),
                product.descriptionEn(),
                product.priceFen(),
                product.listPriceFen(),
                product.credits(),
                product.bonusCredits(),
                product.firstPurchaseBonusCredits(),
                product.firstPurchaseBonusPercent(),
                product.validityDays(),
                product.minPlanCode(),
                product.modelScope(),
                product.sortOrder(),
                product.enabled(),
                product.badgeZh(),
                product.badgeEn(),
                product.metaJson());
    }
}
