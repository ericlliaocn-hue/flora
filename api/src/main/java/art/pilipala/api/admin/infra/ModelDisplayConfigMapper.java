package art.pilipala.api.admin.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.admin.domain.ModelDisplayConfig;

@Mapper
public interface ModelDisplayConfigMapper {

    String COLS = "id, model, capability, display_name, enabled, sort_order, updated_at";

    @Select("SELECT " + COLS + " FROM model_display_config ORDER BY sort_order, model")
    List<ModelDisplayConfig> findAll();

    @Select("SELECT " + COLS + " FROM model_display_config WHERE enabled = true ORDER BY sort_order, model")
    List<ModelDisplayConfig> findEnabled();

    @Select("SELECT " + COLS + " FROM model_display_config WHERE model = #{model}")
    ModelDisplayConfig findByModel(@Param("model") String model);

    @Select("INSERT INTO model_display_config (model, capability, display_name, enabled, sort_order, updated_at) "
            + "VALUES (#{model}, #{capability}, #{displayName}, #{enabled}, #{sortOrder}, now()) "
            + "ON CONFLICT (model) DO UPDATE SET capability = EXCLUDED.capability, "
            + "display_name = EXCLUDED.display_name, enabled = EXCLUDED.enabled, "
            + "sort_order = EXCLUDED.sort_order, updated_at = now() RETURNING " + COLS)
    ModelDisplayConfig upsert(
            @Param("model") String model,
            @Param("capability") String capability,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("sortOrder") int sortOrder);
}
