package com.flora.worker.job.infra;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.worker.job.domain.WorkerJob;
import com.flora.worker.job.domain.WorkerJobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * job 表的 JDBC 实现（worker 侧）。SQL 只在这里。
 *
 * <p>列与 docs/engineering/data-model/jobs.md 一致；worker 不碰 users / wallet 等表。
 */
@Repository
public class JdbcWorkerJobRepository implements WorkerJobRepository {

    private static final String COLUMNS = "id, status, capability, model, params, attempt, max_attempts, "
            + "cancel_requested_at, error_code, updated_at";

    private static final RowMapper<WorkerJob> MAPPER = (rs, rowNum) -> new WorkerJob(
            rs.getObject("id", UUID.class),
            WorkerJobStatus.valueOf(rs.getString("status").toUpperCase(java.util.Locale.ROOT)),
            rs.getString("capability"),
            rs.getString("model"),
            rs.getString("params"),
            rs.getInt("attempt"),
            rs.getInt("max_attempts"),
            toInstant(rs.getTimestamp("cancel_requested_at")),
            rs.getString("error_code"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcWorkerJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkerJob> findById(UUID jobId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM job WHERE id = ?", MAPPER, jobId).stream().findFirst();
    }

    @Override
    public Optional<WorkerJob> claim(UUID jobId) {
        List<WorkerJob> rows = jdbc.query(
                "UPDATE job SET status = 'running', attempt = attempt + 1, updated_at = now() "
                        + "WHERE id = ? AND status = 'queued' RETURNING " + COLUMNS,
                MAPPER,
                jobId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean updateStatus(UUID jobId, WorkerJobStatus from, WorkerJobStatus to, String errorCode) {
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

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
