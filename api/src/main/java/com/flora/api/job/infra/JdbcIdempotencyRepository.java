package com.flora.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.job.domain.IdempotencyRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * idempotency_record 表的 JDBC 实现。SQL 只在这里。
 */
@Repository
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private static final String COLUMNS = "id, user_id, \"key\", job_id, request_hash, created_at";

    private static final RowMapper<IdempotencyRecord> MAPPER = (rs, rowNum) -> new IdempotencyRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("key"),
            rs.getObject("job_id", UUID.class),
            rs.getString("request_hash"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key) {
        List<IdempotencyRecord> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM idempotency_record WHERE user_id = ? AND \"key\" = ?",
                MAPPER,
                userId,
                key);
        return rows.stream().findFirst();
    }

    @Override
    public void insert(UUID userId, String key, UUID jobId, String requestHash) {
        jdbc.update(
                "INSERT INTO idempotency_record (user_id, \"key\", job_id, request_hash) VALUES (?, ?, ?, ?)",
                userId, key, jobId, requestHash);
    }
}
