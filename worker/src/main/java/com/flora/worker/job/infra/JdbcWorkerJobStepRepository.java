package com.flora.worker.job.infra;

import java.util.List;
import java.util.UUID;

import com.flora.worker.job.domain.WorkerStepStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * job_step 表的 JDBC 实现（worker 侧）。SQL 只在这里。
 */
@Repository
public class JdbcWorkerJobStepRepository implements WorkerJobStepRepository {

    private static final RowMapper<UUID> MAPPER = (rs, rowNum) -> rs.getObject("id", UUID.class);

    private final JdbcTemplate jdbc;

    public JdbcWorkerJobStepRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID start(UUID jobId, int attempt) {
        List<UUID> ids = jdbc.query(
                "INSERT INTO job_step (job_id, attempt, status) VALUES (?, ?, 'running') RETURNING id",
                MAPPER,
                jobId,
                attempt);
        return ids.get(0);
    }

    @Override
    public void end(UUID jobId, int attempt, WorkerStepStatus status, String errorCode, UUID providerTaskId) {
        jdbc.update(
                "UPDATE job_step SET status = ?, error_code = ?, provider_task_id = ?, ended_at = now() "
                        + "WHERE job_id = ? AND attempt = ?",
                status.value(), errorCode, providerTaskId, jobId, attempt);
    }
}
