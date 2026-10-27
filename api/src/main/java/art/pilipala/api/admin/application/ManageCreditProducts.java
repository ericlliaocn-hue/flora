package art.pilipala.api.admin.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.infra.CreditGrantRepository;
import art.pilipala.api.billing.infra.CreditProductRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageCreditProducts {

    private final CreditProductRepository products;
    private final CreditGrantRepository grants;

    public ManageCreditProducts(CreditProductRepository products, CreditGrantRepository grants) {
        this.products = products;
        this.grants = grants;
    }

    public List<AdminCreditProductView> list(String tab, Boolean enabled) {
        String tabFilter = tab == null || tab.isBlank() ? null : tab;
        var rows = products.list(tabFilter, false).stream();
        if (enabled == null) {
            return rows.map(AdminCreditProductView::of).toList();
        }
        if (enabled) {
            return rows.filter(CreditProduct::enabled).map(AdminCreditProductView::of).toList();
        }
        return rows.filter(p -> !p.enabled()).map(AdminCreditProductView::of).toList();
    }

    @Transactional
    public AdminCreditProductView create(CreateCreditProductRequest request) {
        validate(request);
        CreditProduct row = products.insert(toDraft(request, 0, true));
        return AdminCreditProductView.of(row);
    }

    @Transactional
    public AdminCreditProductView update(long id, CreateCreditProductRequest request) {
        validate(request);
        CreditProduct existing = products.findById(id).orElseThrow(JobException::validation);
        CreditProduct row = products.update(new CreditProduct(
                id,
                request.code(),
                request.tab(),
                request.nameZh(),
                request.nameEn(),
                request.descriptionZh(),
                request.descriptionEn(),
                request.priceFen(),
                request.listPriceFen(),
                request.credits(),
                request.bonusCredits(),
                request.firstPurchaseBonusCredits(),
                request.firstPurchaseBonusPercent(),
                request.validityDays(),
                request.minPlanCode(),
                request.modelScope(),
                request.sortOrder(),
                existing.enabled(),
                request.badgeZh(),
                request.badgeEn(),
                request.metaJson(),
                existing.createdAt(),
                existing.updatedAt()));
        return AdminCreditProductView.of(row);
    }

    @Transactional
    public AdminCreditProductView setEnabled(long id, boolean enabled) {
        if (!products.setEnabled(id, enabled)) {
            throw JobException.validation();
        }
        return AdminCreditProductView.of(products.findById(id).orElseThrow(JobException::validation));
    }

    public List<AdminCreditGrantView> listGrants(UUID userId, int limit) {
        return grants.listByUser(userId, limit).stream().map(AdminCreditGrantView::of).toList();
    }

    private static void validate(CreateCreditProductRequest request) {
        if (request.code() == null || request.code().isBlank() || request.tab() == null || request.tab().isBlank()) {
            throw JobException.validation();
        }
        if (request.nameZh() == null || request.nameZh().isBlank()) {
            throw JobException.validation();
        }
        if (request.priceFen() <= 0 && !"custom".equals(request.tab())) {
            throw JobException.validation();
        }
    }

    private static CreditProduct toDraft(CreateCreditProductRequest request, long id, boolean enabled) {
        return new CreditProduct(
                id,
                request.code(),
                request.tab(),
                request.nameZh(),
                request.nameEn(),
                request.descriptionZh(),
                request.descriptionEn(),
                request.priceFen(),
                request.listPriceFen(),
                request.credits(),
                request.bonusCredits(),
                request.firstPurchaseBonusCredits(),
                request.firstPurchaseBonusPercent(),
                request.validityDays(),
                request.minPlanCode(),
                request.modelScope(),
                request.sortOrder(),
                enabled,
                request.badgeZh(),
                request.badgeEn(),
                request.metaJson(),
                null,
                null);
    }

    public record CreateCreditProductRequest(
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
            String badgeZh,
            String badgeEn,
            String metaJson) {
    }

    public record AdminCreditGrantView(
            long id,
            String sourceType,
            String grantType,
            long creditsTotal,
            long creditsRemaining,
            String expiresAt,
            Long productId) {

        static AdminCreditGrantView of(CreditGrant grant) {
            return new AdminCreditGrantView(
                    grant.id(),
                    grant.sourceType(),
                    grant.grantType(),
                    grant.creditsTotal(),
                    grant.creditsRemaining(),
                    grant.expiresAt() == null ? null : grant.expiresAt().toString(),
                    grant.productId());
        }
    }
}
