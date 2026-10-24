package com.flora.api.job.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.flora.api.job.domain.JobStep;
import com.flora.api.job.domain.JobStepStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * job_step 表的 JDBC 实现。SQL 只在这里。
 */
@Repository
public class JdbcJobStepRepository implements JobStepRepository {

    private static final String COLUMNS = "id, job_id, attempt, status, error_code, provider_task_id, started_at, ended_at";

    private static final RowMapper<JobStep> MAPPER = (rs, rowNum) -> new JobStep(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getInt("attempt"),
            JobStepStatus.of(rs.getString("status")),
            rs.getString("error_code"),
            rs.getObject("provider_task_id", UUID.class),
            rs.getTimestamp("started_at").toInstant(),
            toInstant(rs.getTimestamp("ended_at")));

    private final JdbcTemplate jdbc;

    public JdbcJobStepRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public JobStep start(UUID jobId, int attempt) {
        List<JobStep> rows = jdbc.query(
                "INSERT INTO job_step (job_id, attempt, status) VALUES (?, ?, 'running') RETURNING " + COLUMNS,
                MAPPER,
                jobId,
                attempt);
        return rows.get(0);
    }

    @Override
    public void end(UUID jobId, int attempt, JobStepStatus status, String errorCode) {
        jdbc.update(
                "UPDATE job_step SET status = ?, error_code = ?, ended_at = now() WHERE job_id = ? AND attempt = ?",
                status.value(), errorCode, jobId, attempt);
    }

    @Override
    public List<JobStep> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", jobIds.stream().map(id -> "?").toList());
        return jdbc.query("SELECT " + COLUMNS + " FROM job_step WHERE job_id IN (" + placeholders + ") "
                + "ORDER BY job_id, attempt", MAPPER, jobIds.toArray());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
