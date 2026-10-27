package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.CreditProduct;

@Repository
public class MybatisCreditProductRepository implements CreditProductRepository {

    private final CreditProductMapper mapper;

    public MybatisCreditProductRepository(CreditProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CreditProduct> findByCode(String code) {
        return Optional.ofNullable(mapper.findByCode(code));
    }

    @Override
    public Optional<CreditProduct> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<CreditProduct> list(String tab, boolean enabledOnly) {
        return mapper.list(tab, enabledOnly);
    }

    @Override
    public CreditProduct insert(CreditProduct draft) {
        return mapper.insert(
                draft.code(),
                draft.tab(),
                draft.nameZh(),
                draft.nameEn(),
                draft.descriptionZh(),
                draft.descriptionEn(),
                draft.priceFen(),
                draft.listPriceFen(),
                draft.credits(),
                draft.bonusCredits(),
                draft.firstPurchaseBonusCredits(),
                draft.firstPurchaseBonusPercent(),
                draft.validityDays(),
                draft.minPlanCode(),
                draft.modelScope(),
                draft.sortOrder(),
                draft.enabled(),
                draft.badgeZh(),
                draft.badgeEn(),
                draft.metaJson());
    }

    @Override
    public CreditProduct update(CreditProduct row) {
        return mapper.update(
                row.id(),
                row.tab(),
                row.nameZh(),
                row.nameEn(),
                row.descriptionZh(),
                row.descriptionEn(),
                row.priceFen(),
                row.listPriceFen(),
                row.credits(),
                row.bonusCredits(),
                row.firstPurchaseBonusCredits(),
                row.firstPurchaseBonusPercent(),
                row.validityDays(),
                row.minPlanCode(),
                row.modelScope(),
                row.sortOrder(),
                row.badgeZh(),
                row.badgeEn(),
                row.metaJson());
    }

    @Override
    public boolean setEnabled(long id, boolean enabled) {
        return mapper.setEnabled(id, enabled) > 0;
    }

    @Override
    public long countAll() {
        return mapper.countAll();
    }
}
