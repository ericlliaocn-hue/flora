package com.flora.api.project.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.flora.api.project.domain.AssetSource;
import com.flora.api.project.domain.UserAsset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * user_asset 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>表结构由 Flyway 的 V6__create_cloud_persistence.sql 建，列与
 * docs/engineering/data-model/projects.md 一致。
 */
@Repository
public class JdbcUserAssetRepository implements UserAssetRepository {

    private static final String COLUMNS = "id, user_id, project_id, job_id, source, object_key, mime_type, created_at";

    private static final RowMapper<UserAsset> MAPPER = (rs, rowNum) -> new UserAsset(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("job_id", UUID.class),
            AssetSource.of(rs.getString("source")),
            rs.getString("object_key"),
            rs.getString("mime_type"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcUserAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertGeneratedIfAbsent(UUID userId, UUID jobId, String objectKey, String mimeType) {
        jdbc.update(
                "INSERT INTO user_asset (user_id, job_id, source, object_key, mime_type) VALUES (?, ?, 'generated', ?, ?) "
                        + "ON CONFLICT (job_id, object_key) DO NOTHING",
                userId, jobId, objectKey, mimeType);
    }

    @Override
    public List<UserAsset> findByUserAssets(UUID userId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_asset WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, userId, limit);
    }

    @Override
    public boolean deleteAsset(UUID assetId, UUID userId) {
        return jdbc.update("DELETE FROM user_asset WHERE id = ? AND user_id = ?", assetId, userId) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
