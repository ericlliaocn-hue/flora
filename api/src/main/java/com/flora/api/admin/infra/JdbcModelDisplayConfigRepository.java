package com.flora.api.admin.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.flora.api.admin.domain.ModelDisplayConfig;

/**
 * 模型展示配置的 JDBC 实现。SQL 只在这里。
 */
@Repository
public class JdbcModelDisplayConfigRepository implements ModelDisplayConfigRepository {
    private static final String COLUMNS = "id, model, capability, display_name, enabled, sort_order, updated_at";

    private static final RowMapper<ModelDisplayConfig> MAPPER = (rs, rowNum) -> new ModelDisplayConfig(
            rs.getObject("id", UUID.class),
            rs.getString("model"),
            rs.getString("capability"),
            rs.getString("display_name"),
            rs.getBoolean("enabled"),
            rs.getInt("sort_order"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcModelDisplayConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ModelDisplayConfig> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM model_display_config ORDER BY sort_order, model", MAPPER);
    }

    @Override
    public List<ModelDisplayConfig> findEnabled() {
        return jdbc.query("SELECT " + COLUMNS + " FROM model_display_config WHERE enabled = true ORDER BY sort_order, model",
                MAPPER);
    }

    @Override
    public Optional<ModelDisplayConfig> findByModel(String model) {
        List<ModelDisplayConfig> rows = jdbc.query("SELECT " + COLUMNS + " FROM model_display_config WHERE model = ?",
                MAPPER, model);
        return rows.stream().findFirst();
    }

    @Override
    public ModelDisplayConfig upsert(String model, String capability, String displayName, boolean enabled, int sortOrder) {
        List<ModelDisplayConfig> rows = jdbc.query(
                "INSERT INTO model_display_config (model, capability, display_name, enabled, sort_order, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (model) DO UPDATE SET capability = EXCLUDED.capability, "
                        + "display_name = EXCLUDED.display_name, enabled = EXCLUDED.enabled, "
                        + "sort_order = EXCLUDED.sort_order, updated_at = now() RETURNING " + COLUMNS,
                MAPPER, model, capability, displayName, enabled, sortOrder);
        return rows.get(0);
    }
}
