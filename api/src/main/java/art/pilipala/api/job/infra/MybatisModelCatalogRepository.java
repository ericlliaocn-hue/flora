package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;

@Repository
public class MybatisModelCatalogRepository implements ModelCatalogRepository {

    private final ModelCatalogMapper mapper;

    public MybatisModelCatalogRepository(ModelCatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ModelProduct> findProductByCode(String code) {
        return Optional.ofNullable(mapper.findProductByCode(code));
    }

    @Override
    public List<ModelRoute> findRoutesByProduct(UUID productId) {
        return mapper.findRoutesByProduct(productId);
    }

    @Override
    public Optional<ModelTarget> findTargetById(UUID targetId) {
        return Optional.ofNullable(mapper.findTargetById(targetId));
    }
}
