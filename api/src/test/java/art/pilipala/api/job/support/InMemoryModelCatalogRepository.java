package art.pilipala.api.job.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.ModelCatalogRepository;

/** 内存版产品目录：只读用例测试，不依赖 Postgres。 */
public class InMemoryModelCatalogRepository implements ModelCatalogRepository {

    private final Map<String, ModelProduct> products = new LinkedHashMap<>();
    private final List<ModelProductGroup> groups = new ArrayList<>();

    public InMemoryModelCatalogRepository product(ModelProduct product) {
        products.put(product.code(), product);
        return this;
    }

    public InMemoryModelCatalogRepository group(ModelProductGroup group) {
        groups.add(group);
        return this;
    }

    @Override
    public Optional<ModelProduct> findProductByCode(String code) {
        return Optional.ofNullable(products.get(code));
    }

    @Override
    public List<ModelProduct> findAllProducts() {
        return List.copyOf(products.values());
    }

    @Override
    public List<ModelProductGroup> findGroupsByProduct(UUID productId) {
        return groups.stream()
                .filter(ModelProductGroup::enabled)
                .filter(group -> group.productId().equals(productId))
                .sorted(Comparator.comparingInt(ModelProductGroup::priority))
                .toList();
    }

    @Override
    public List<ModelProductGroup> findAllGroups() {
        return List.copyOf(groups);
    }
}
