package art.pilipala.api.billing.application;

import java.util.List;
import java.util.Optional;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.infra.CreditProductRepository;
import art.pilipala.api.billing.infra.RechargePackageProperties;
import art.pilipala.api.billing.infra.RechargeProperties;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Component;

/**
 * 充值商品目录：DB {@code credit_product} 优先；库空时回退 yml {@code recharge.packages}。
 */
@Component
public class RechargeProductCatalog {

    private final CreditProductRepository products;
    private final RechargeProperties rechargeProperties;

    public RechargeProductCatalog(CreditProductRepository products, RechargeProperties rechargeProperties) {
        this.products = products;
        this.rechargeProperties = rechargeProperties;
    }

    public boolean isDbPrimary() {
        return products.countAll() > 0;
    }

    public CreditProduct requireEnabled(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            throw JobException.validation();
        }
        if (isDbPrimary()) {
            return products.findByCode(productCode)
                    .filter(CreditProduct::enabled)
                    .orElseThrow(JobException::validation);
        }
        throw JobException.validation();
    }

    public Optional<CreditProduct> findEnabled(String productCode) {
        if (!isDbPrimary()) {
            return Optional.empty();
        }
        return products.findByCode(productCode).filter(CreditProduct::enabled);
    }

    public RechargePackageProperties requireLegacyPackage(String packageId) {
        if (packageId == null || packageId.isBlank() || rechargeProperties.packages() == null) {
            throw JobException.validation();
        }
        return rechargeProperties.packages().stream()
                .filter(pkg -> pkg.id().equals(packageId))
                .findFirst()
                .orElseThrow(JobException::validation);
    }

    public List<RechargePackageProperties> legacyPackages() {
        if (rechargeProperties.packages() == null) {
            return List.of();
        }
        return rechargeProperties.packages();
    }
}
