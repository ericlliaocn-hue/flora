package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;

@Mapper
public interface ModelCatalogMapper {

    String PRODUCT_COLS = "id, code, capability, display_name, description, price_credits, "
            + "param_schema AS param_schema_json, enabled, sort_order";

    @Select("SELECT " + PRODUCT_COLS + " FROM model_product WHERE code = #{code}")
    ModelProduct findProductByCode(@Param("code") String code);

    @Select("SELECT " + PRODUCT_COLS + " FROM model_product ORDER BY sort_order ASC, code ASC")
    List<ModelProduct> findAllProducts();

    String GROUP_COLS = "id, product_id, group_code, model_code, priority, timeout_seconds, max_cost_amount, enabled";

    @Select("SELECT " + GROUP_COLS + " FROM model_product_group "
            + "WHERE product_id = #{productId} AND enabled = true ORDER BY priority ASC")
    List<ModelProductGroup> findGroupsByProduct(@Param("productId") UUID productId);

    @Select("SELECT " + GROUP_COLS + " FROM model_product_group ORDER BY product_id ASC, priority ASC")
    List<ModelProductGroup> findAllGroups();
}
