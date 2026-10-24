package com.flora.worker.job.infra;

import java.sql.Types;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * outbox_message 表的 JDBC 实现（worker 侧）。SQL 只在这里。
 */
@Repository
public class JdbcWorkerOutboxRepository implements WorkerOutboxRepository {

    private final JdbcTemplate jdbc;

    public JdbcWorkerOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        jdbc.update(
                "INSERT INTO outbox_message (id, aggregate_type, aggregate_id, payload, status) "
                        + "VALUES (?, ?, ?, ?, 'pending')",
                ps -> {
                    ps.setObject(1, id);
                    ps.setString(2, aggregateType);
                    ps.setObject(3, aggregateId);
                    ps.setObject(4, payloadJson, Types.OTHER);
                });
    }
}
