package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.Optional;

import art.pilipala.api.billing.domain.CreditProduct;

public interface CreditProductRepository {

    Optional<CreditProduct> findByCode(String code);

    Optional<CreditProduct> findById(long id);

    List<CreditProduct> list(String tab, boolean enabledOnly);

    CreditProduct insert(CreditProduct draft);

    CreditProduct update(CreditProduct row);

    boolean setEnabled(long id, boolean enabled);

    long countAll();
}
