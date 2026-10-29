package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;

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
    public List<ModelProduct> findAllProducts() {
        return mapper.findAllProducts();
    }

    @Override
    public List<ModelProductGroup> findGroupsByProduct(UUID productId) {
        return mapper.findGroupsByProduct(productId);
    }

    @Override
    public List<ModelProductGroup> findAllGroups() {
        return mapper.findAllGroups();
    }
}
