package art.pilipala.api.settings.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppRuntimeSettingMapper {

    String COLS = "key, value, updated_at";

    @Select("SELECT " + COLS + " FROM app_runtime_setting WHERE key = #{key}")
    AppRuntimeSettingRow findByKey(@Param("key") String key);

    @Select("SELECT " + COLS + " FROM app_runtime_setting ORDER BY key")
    List<AppRuntimeSettingRow> findAll();

    @Select("INSERT INTO app_runtime_setting (key, value, updated_at) VALUES (#{key}, #{value}, now()) "
            + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now() "
            + "RETURNING " + COLS)
    AppRuntimeSettingRow upsert(@Param("key") String key, @Param("value") String value);

    record AppRuntimeSettingRow(String key, String value, java.time.Instant updatedAt) {
    }
}
