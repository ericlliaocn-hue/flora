package com.flora.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flora.api.job.domain.ProviderTask;

/**
 * provider_task 的 JDBC 读取实现。SQL 只在这里。
 *
 * <p>原文（raw_status / raw_body）只从这里流向 {@code /api/v1/admin/**}（ADR 0005）。
 */
@Repository
public class JdbcProviderTaskRepository implements ProviderTaskRepository {
    private static final String COLUMNS = "id, job_id, job_step_id, upstream_request_id, raw_status, raw_body, created_at";

    private static final RowMapper<ProviderTask> MAPPER = (rs, rowNum) -> new ProviderTask(
            rs.getObject("id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getObject("job_step_id", UUID.class),
            rs.getString("upstream_request_id"),
            rs.getString("raw_status"),
            readJson(rs.getString("raw_body")),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private static final ObjectMapper mapper = new ObjectMapper();

    public JdbcProviderTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProviderTask> findByJobId(UUID jobId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM provider_task WHERE job_id = ? ORDER BY created_at",
                MAPPER, jobId);
    }

    @Override
    public List<ProviderTask> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", jobIds.stream().map(id -> "?").toList());
        return jdbc.query("SELECT " + COLUMNS + " FROM provider_task WHERE job_id IN (" + placeholders + ") "
                + "ORDER BY job_id, created_at", MAPPER, jobIds.toArray());
    }

    private static JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            // 原文不是合法 JSON 时按文本回显，不让排障页面整个炸掉。
            return mapper.getNodeFactory().textNode(raw);
        }
    }
}
