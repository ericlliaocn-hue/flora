package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;

/**
 * 视频产品 / target / 路由的读取契约。
 *
 * <p>只读：运营配置走 SQL / 后续 Admin PATCH；本期不在请求里写这三张表。
 */
public interface ModelCatalogRepository {

    Optional<ModelProduct> findProductByCode(String code);

    List<ModelRoute> findRoutesByProduct(UUID productId);

    Optional<ModelTarget> findTargetById(UUID targetId);
}
