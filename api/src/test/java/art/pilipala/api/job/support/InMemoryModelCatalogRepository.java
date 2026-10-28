package art.pilipala.api.job.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;
import art.pilipala.api.job.infra.ModelCatalogRepository;

/** 内存版产品目录：只读用例测试，不依赖 Postgres。 */
public class InMemoryModelCatalogRepository implements ModelCatalogRepository {

    private final Map<String, ModelProduct> products = new LinkedHashMap<>();
    private final Map<UUID, ModelTarget> targets = new LinkedHashMap<>();
    private final List<ModelRoute> routes = new ArrayList<>();

    public InMemoryModelCatalogRepository product(ModelProduct product) {
        products.put(product.code(), product);
        return this;
    }

    public InMemoryModelCatalogRepository target(ModelTarget target) {
        targets.put(target.id(), target);
        return this;
    }

    public InMemoryModelCatalogRepository route(ModelRoute route) {
        routes.add(route);
        return this;
    }

    @Override
    public Optional<ModelProduct> findProductByCode(String code) {
        return Optional.ofNullable(products.get(code));
    }

    @Override
    public List<ModelRoute> findRoutesByProduct(UUID productId) {
        return routes.stream()
                .filter(ModelRoute::enabled)
                .filter(route -> route.productId().equals(productId))
                .sorted(Comparator.comparingInt(ModelRoute::priority))
                .toList();
    }

    @Override
    public Optional<ModelTarget> findTargetById(UUID targetId) {
        return Optional.ofNullable(targets.get(targetId));
    }
}
