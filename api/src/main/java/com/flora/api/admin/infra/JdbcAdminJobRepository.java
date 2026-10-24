package com.flora.api.admin.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.flora.api.admin.domain.AdminJob;

/**
 * 运维后台任务查询的 JDBC 实现。SQL 只在这里。
 *
 * <p>联表 users 取用户名、wallet_hold 取冻结单状态；prompt 直接从
 * {@code job.params} 的 JSONB 里取，缺失就是空串。
 */
@Repository
public class JdbcAdminJobRepository implements AdminJobRepository {
    private static final String COLUMNS = "j.id, j.user_id, u.username, j.status, j.capability, j.model, "
            + "COALESCE(j.params->>'prompt', '') AS prompt, j.attempt, j.max_attempts, j.hold_id, "
            + "h.status AS hold_status, j.error_code, j.created_at, j.updated_at";

    private static final String FROM = " FROM job j "
            + "JOIN users u ON u.id = j.user_id "
            + "LEFT JOIN wallet_hold h ON h.id = j.hold_id ";

    private static final RowMapper<AdminJob> MAPPER = (rs, rowNum) -> new AdminJob(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("username"),
            rs.getString("status"),
            rs.getString("capability"),
            rs.getString("model"),
            rs.getString("prompt"),
            rs.getInt("attempt"),
            rs.getInt("max_attempts"),
            rs.getObject("hold_id", UUID.class),
            rs.getString("hold_status"),
            rs.getString("error_code"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcAdminJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminJob> findJobs(String status, UUID userId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(FROM).append("WHERE 1 = 1");
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND j.status = ?");
            args.add(status.trim());
        }
        if (userId != null) {
            sql.append(" AND j.user_id = ?");
            args.add(userId);
        }
        sql.append(" ORDER BY j.created_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    @Override
    public Optional<AdminJob> findJob(UUID jobId) {
        List<AdminJob> rows = jdbc.query("SELECT " + COLUMNS + FROM + "WHERE j.id = ?", MAPPER, jobId);
        return rows.stream().findFirst();
    }
}
