package art.pilipala.api.admin.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.domain.ModelDisplayConfig;

/**
 * 模型展示配置的存取。SQL 只在 JDBC 实现里。
 */
public interface ModelDisplayConfigRepository {
    /** 全部配置，按 sort_order、model 升序。 */
    List<ModelDisplayConfig> findAll();

    /** 只看上架的（enabled = true），按 sort_order、model 升序。 */
    List<ModelDisplayConfig> findEnabled();

    /** 按 model 取一行。 */
    Optional<ModelDisplayConfig> findByModel(String model);

    /** 写入或覆盖一行；model 是唯一键。 */
    ModelDisplayConfig upsert(String model, String capability, String displayName, boolean enabled, int sortOrder);
}
