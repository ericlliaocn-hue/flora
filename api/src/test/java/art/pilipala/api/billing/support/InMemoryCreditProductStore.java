package art.pilipala.api.billing.support;

import java.util.List;
import java.util.Optional;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.infra.CreditProductRepository;

public class InMemoryCreditProductStore implements CreditProductRepository {

    @Override
    public Optional<CreditProduct> findByCode(String code) {
        return Optional.empty();
    }

    @Override
    public Optional<CreditProduct> findById(long id) {
        return Optional.empty();
    }

    @Override
    public List<CreditProduct> list(String tab, boolean enabledOnly) {
        return List.of();
    }

    @Override
    public CreditProduct insert(CreditProduct draft) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreditProduct update(CreditProduct row) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setEnabled(long id, boolean enabled) {
        return false;
    }

    @Override
    public long countAll() {
        return 0;
    }
}
