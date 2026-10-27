package art.pilipala.api.billing.application;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ListRechargePackages {

    private final ListCreditProducts products;

    public ListRechargePackages(ListCreditProducts products) {
        this.products = products;
    }

    public List<RechargePackageView> list() {
        return products.listEnabled(null).stream()
                .map(p -> new RechargePackageView(
                        p.code() != null ? p.code() : String.valueOf(p.id()),
                        p.priceFen(),
                        p.credits(),
                        p.nameZh()))
                .toList();
    }
}
