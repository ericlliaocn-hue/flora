package art.pilipala.api.billing.application;

import art.pilipala.api.billing.domain.CreditProduct;

public record CreditProductView(
        Long id,
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
        Integer firstPurchaseBonusPercent,
        Integer validityDays,
        String minPlanCode,
        String badgeZh,
        String badgeEn,
        String metaJson,
        int sortOrder) {

    public static CreditProductView of(CreditProduct product) {
        return new CreditProductView(
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
                product.firstPurchaseBonusPercent(),
                product.validityDays(),
                product.minPlanCode(),
                product.badgeZh(),
                product.badgeEn(),
                product.metaJson(),
                product.sortOrder());
    }

    public static CreditProductView legacy(String code, String name, long moneyFen, long credits) {
        return new CreditProductView(
                null, code, "general", name, name, null, null, (int) moneyFen, null, credits, 0, null, null, null,
                null, null, null, 0);
    }
}
