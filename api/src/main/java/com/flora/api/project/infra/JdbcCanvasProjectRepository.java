package com.flora.api.project.infra;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.project.domain.CanvasProject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * canvas_project 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>表结构由 Flyway 的 V6__create_cloud_persistence.sql 建，列与
 * docs/engineering/data-model/projects.md 一致。
 */
@Repository
public class JdbcCanvasProjectRepository implements CanvasProjectRepository {

    private static final String COLUMNS = "id, user_id, name, canvas_data, version, created_at, updated_at";

    private static final RowMapper<CanvasProject> MAPPER = (rs, rowNum) -> new CanvasProject(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("name"),
            rs.getString("canvas_data"),
            rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcCanvasProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CanvasProject insert(UUID userId, String name, String canvasDataJson) {
        List<CanvasProject> rows = jdbc.query(
                "INSERT INTO canvas_project (user_id, name, canvas_data) VALUES (?, ?, ?) RETURNING " + COLUMNS,
                ps -> {
                    ps.setObject(1, userId);
                    ps.setString(2, name);
                    ps.setObject(3, canvasDataJson, Types.OTHER);
                },
                MAPPER);
        return rows.get(0);
    }

    @Override
    public Optional<CanvasProject> findByIdAndUser(UUID projectId, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM canvas_project WHERE id = ? AND user_id = ?", MAPPER, projectId, userId)
                .stream()
                .findFirst();
    }

    @Override
    public List<CanvasProject> findByUser(UUID userId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM canvas_project WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
                MAPPER, userId, limit);
    }

    @Override
    public Optional<CanvasProject> update(UUID projectId, UUID userId, String name, String canvasDataJson) {
        List<CanvasProject> rows = jdbc.query(
                "UPDATE canvas_project SET name = COALESCE(?, name), canvas_data = COALESCE(?, canvas_data), "
                        + "version = version + 1, updated_at = now() WHERE id = ? AND user_id = ? RETURNING " + COLUMNS,
                ps -> {
                    if (name == null) {
                        ps.setNull(1, Types.VARCHAR);
                    } else {
                        ps.setString(1, name);
                    }
                    if (canvasDataJson == null) {
                        ps.setNull(2, Types.OTHER);
                    } else {
                        ps.setObject(2, canvasDataJson, Types.OTHER);
                    }
                    ps.setObject(3, projectId);
                    ps.setObject(4, userId);
                },
                MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public boolean delete(UUID projectId, UUID userId) {
        return jdbc.update("DELETE FROM canvas_project WHERE id = ? AND user_id = ?", projectId, userId) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
