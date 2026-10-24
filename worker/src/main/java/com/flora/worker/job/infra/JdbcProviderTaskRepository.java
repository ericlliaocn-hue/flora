package com.flora.worker.job.infra;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * provider_task 表的 JDBC 实现（worker 侧）。SQL 只在这里。
 *
 * <p>列与 docs/engineering/data-model/jobs.md 一致：只有 7 列，结构化摘要统一放进
 * raw_body JSONB，不自造 provider / http_status 之类的列。
 */
@Repository
public class JdbcProviderTaskRepository implements ProviderTaskRepository {

    private static final RowMapper<UUID> MAPPER = (rs, rowNum) -> rs.getObject("id", UUID.class);

    private final JdbcTemplate jdbc;

    public JdbcProviderTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID insert(UUID jobId, UUID jobStepId, String upstreamRequestId, String rawStatus, String rawBodyJson) {
        List<UUID> ids = jdbc.query(
                "INSERT INTO provider_task (job_id, job_step_id, upstream_request_id, raw_status, raw_body) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                ps -> {
                    ps.setObject(1, jobId);
                    ps.setObject(2, jobStepId);
                    ps.setString(3, upstreamRequestId);
                    ps.setString(4, rawStatus);
                    ps.setObject(5, rawBodyJson, Types.OTHER);
                },
                MAPPER);
        return ids.get(0);
    }
}
