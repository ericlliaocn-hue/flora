package art.pilipala.api.job.application;

import java.util.Comparator;
import java.util.Optional;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import org.springframework.stereotype.Service;

/**
 * 产品 → NewAPI group/model 的只读解析。不冻结、不建 job。
 *
 * <p>产品不存在/下架由调用方判定；没有 enabled group → {@code NO_AVAILABLE_CHANNEL}。
 */
@Service
public class ModelCatalog {

    private final ModelCatalogRepository repository;

    public ModelCatalog(ModelCatalogRepository repository) {
        this.repository = repository;
    }

    public Optional<ModelProduct> findProduct(String productCode) {
        String code = productCode == null ? "" : productCode.trim();
        return code.isEmpty() ? Optional.empty() : repository.findProductByCode(code);
    }

    /** Phase 2：产品 → NewAPI group/model；缺省按 priority 取第一个 enabled。 */
    public ResolvedGroup resolveGroup(ModelProduct product) {
        return repository.findGroupsByProduct(product.id()).stream()
                .filter(ModelProductGroup::enabled)
                .min(Comparator.comparingInt(ModelProductGroup::priority))
                .map(ResolvedGroup::new)
                .orElseThrow(JobException::noAvailableChannel);
    }

    public record ResolvedGroup(ModelProductGroup group) {
    }
}
