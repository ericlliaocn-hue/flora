package art.pilipala.api.job.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import org.springframework.stereotype.Service;

/**
 * 产品 → 有序 target 的解析。只读，不冻结、不建 job。
 *
 * <p>规则：
 * <ul>
 *   <li>产品不存在 / 已下架 → {@code VALIDATION_ERROR}；</li>
 *   <li>route 按 priority 升序；禁用的 route / target 跳过；</li>
 *   <li>一个可用 target 都没有 → {@code NO_AVAILABLE_CHANNEL}。</li>
 * </ul>
 */
@Service
public class ModelCatalog {

    private final ModelCatalogRepository repository;

    public ModelCatalog(ModelCatalogRepository repository) {
        this.repository = repository;
    }

    public ResolvedProduct resolve(String productCode) {
        String code = productCode == null ? "" : productCode.trim();
        ModelProduct product = repository.findProductByCode(code)
                .filter(ModelProduct::enabled)
                .orElseThrow(JobException::validation);
        List<ResolvedTarget> targets = repository.findRoutesByProduct(product.id()).stream()
                .filter(ModelRoute::enabled)
                .sorted(Comparator.comparingInt(ModelRoute::priority))
                .map(route -> repository.findTargetById(route.targetId())
                        .filter(ModelTarget::enabled)
                        .map(target -> new ResolvedTarget(target, route.priority(), route.maxCostAmount()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        if (targets.isEmpty()) {
            throw JobException.noAvailableChannel();
        }
        return new ResolvedProduct(product, targets);
    }

    public record ResolvedProduct(ModelProduct product, List<ResolvedTarget> targets) {

        public ResolvedTarget primary() {
            return targets.getFirst();
        }
    }

    public record ResolvedTarget(ModelTarget target, int priority, BigDecimal maxCostAmount) {
    }
}
