package com.flora.api.job.infra;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * job 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>表结构由 Flyway 的 V2__create_job_domain.sql 建，列与
 * docs/engineering/data-model/jobs.md 一致。
 */
@Repository
public class JdbcJobRepository implements JobRepository {

    private static final String COLUMNS = "id, user_id, status, capability, model, params, attempt, max_attempts, "
            + "hold_id, idempotency_key, cancel_requested_at, error_code, result_object_keys, created_at, updated_at";

    private static final RowMapper<Job> MAPPER = (rs, rowNum) -> new Job(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            JobStatus.of(rs.getString("status")),
            rs.getString("capability"),
            rs.getString("model"),
            rs.getString("params"),
            rs.getInt("attempt"),
            rs.getInt("max_attempts"),
            rs.getObject("hold_id", UUID.class),
            rs.getString("idempotency_key"),
            toInstant(rs.getTimestamp("cancel_requested_at")),
            rs.getString("error_code"),
            rs.getString("result_object_keys"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Job> insertIfAbsent(UUID userId, String capability, String model, String paramsJson, String idempotencyKey,
            UUID holdId) {
        List<Job> rows = jdbc.query(
                "INSERT INTO job (user_id, status, capability, model, params, idempotency_key, hold_id) "
                        + "VALUES (?, 'created', ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id, idempotency_key) DO NOTHING RETURNING " + COLUMNS,
                ps -> {
                    ps.setObject(1, userId);
                    ps.setString(2, capability);
                    ps.setString(3, model);
                    ps.setObject(4, paramsJson, Types.OTHER);
                    ps.setString(5, idempotencyKey);
                    if (holdId == null) {
                        ps.setNull(6, Types.OTHER);
                    } else {
                        ps.setObject(6, holdId);
                    }
                },
                MAPPER);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM job WHERE id = ?", MAPPER, jobId).stream().findFirst();
    }

    @Override
    public Optional<Job> findByIdAndUser(UUID jobId, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM job WHERE id = ? AND user_id = ?", MAPPER, jobId, userId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Job> claim(UUID jobId) {
        List<Job> rows = jdbc.query(
                "UPDATE job SET status = 'running', attempt = attempt + 1, updated_at = now() "
                        + "WHERE id = ? AND status = 'queued' RETURNING " + COLUMNS,
                MAPPER,
                jobId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode) {
        int updated = jdbc.update(
                "UPDATE job SET status = ?, error_code = ?, updated_at = now() WHERE id = ? AND status = ?",
                to.value(), errorCode, jobId, from.value());
        return updated > 0;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        int updated = jdbc.update(
                "UPDATE job SET result_object_keys = ?, updated_at = now() WHERE id = ? AND status = 'running'",
                ps -> {
                    ps.setObject(1, resultObjectKeysJson, Types.OTHER);
                    ps.setObject(2, jobId);
                });
        return updated > 0;
    }

    @Override
    public boolean requestCancel(UUID jobId) {
        int updated = jdbc.update(
                "UPDATE job SET cancel_requested_at = COALESCE(cancel_requested_at, now()), updated_at = now() WHERE id = ?",
                jobId);
        return updated > 0;
    }

    @Override
    public List<Job> findByUserOrderByCreatedAtDesc(UUID userId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM job WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, userId, limit);
    }

    @Override
    public boolean requeueForAdmin(UUID jobId, boolean clearHoldId) {
        String sql = "UPDATE job SET status = 'queued', error_code = NULL, cancel_requested_at = NULL, "
                + "max_attempts = GREATEST(max_attempts, attempt) + 1, updated_at = now() "
                + (clearHoldId ? ", hold_id = NULL " : " ")
                + "WHERE id = ? AND status IN ('dead', 'failed')";
        return jdbc.update(sql, jobId) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
