package art.pilipala.api.billing.application;

import java.util.List;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.infra.CreditProductRepository;
import org.springframework.stereotype.Service;

@Service
public class ListCreditProducts {

    private final CreditProductRepository products;
    private final RechargeProductCatalog catalog;

    public ListCreditProducts(CreditProductRepository products, RechargeProductCatalog catalog) {
        this.products = products;
        this.catalog = catalog;
    }

    public List<CreditProductView> listEnabled(String tab) {
        if (!catalog.isDbPrimary()) {
            return catalog.legacyPackages().stream()
                    .map(pkg -> CreditProductView.legacy(pkg.id(), pkg.name(), pkg.moneyFen(), pkg.credits()))
                    .toList();
        }
        return products.list(tab, true).stream().map(CreditProductView::of).toList();
    }
}
