package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelRoute;
import art.pilipala.api.job.domain.ModelTarget;

@Mapper
public interface ModelCatalogMapper {

    String PRODUCT_COLS = "id, code, capability, display_name, description, price_credits, "
            + "param_schema AS param_schema_json, enabled, sort_order";

    @Select("SELECT " + PRODUCT_COLS + " FROM model_product WHERE code = #{code}")
    ModelProduct findProductByCode(@Param("code") String code);

    @Select("SELECT " + PRODUCT_COLS + " FROM model_product ORDER BY sort_order ASC, code ASC")
    List<ModelProduct> findAllProducts();

    String ROUTE_COLS = "id, product_id, target_id, priority, max_cost_amount, enabled";

    @Select("SELECT " + ROUTE_COLS + " FROM model_route "
            + "WHERE product_id = #{productId} AND enabled = true ORDER BY priority ASC")
    List<ModelRoute> findRoutesByProduct(@Param("productId") UUID productId);

    String TARGET_COLS = "id, code, gateway, upstream_model, constraints AS constraints_json, "
            + "cost_type, cost_amount, cost_currency, timeout_seconds, enabled";

    @Select("SELECT " + TARGET_COLS + " FROM model_target WHERE id = #{targetId}")
    ModelTarget findTargetById(@Param("targetId") UUID targetId);

    @Select("SELECT " + TARGET_COLS + " FROM model_target ORDER BY code ASC")
    List<ModelTarget> findAllTargets();
}
