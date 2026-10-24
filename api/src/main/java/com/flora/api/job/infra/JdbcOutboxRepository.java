package com.flora.api.job.infra;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.flora.api.job.domain.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * outbox_message 表的 JDBC 实现。SQL 只在这里。
 */
@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private static final String COLUMNS = "id, aggregate_type, aggregate_id, payload, status, created_at, sent_at";

    private static final RowMapper<OutboxMessage> MAPPER = (rs, rowNum) -> new OutboxMessage(
            rs.getObject("id", UUID.class),
            rs.getString("aggregate_type"),
            rs.getObject("aggregate_id", UUID.class),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            toInstant(rs.getTimestamp("sent_at")));

    private final JdbcTemplate jdbc;

    public JdbcOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OutboxMessage insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        List<OutboxMessage> rows = jdbc.query(
                "INSERT INTO outbox_message (id, aggregate_type, aggregate_id, payload, status) "
                        + "VALUES (?, ?, ?, ?, 'pending') RETURNING " + COLUMNS,
                ps -> {
                    ps.setObject(1, id);
                    ps.setString(2, aggregateType);
                    ps.setObject(3, aggregateId);
                    ps.setObject(4, payloadJson, Types.OTHER);
                },
                MAPPER);
        return rows.get(0);
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM outbox_message WHERE status = 'pending' ORDER BY created_at LIMIT ?",
                MAPPER,
                limit);
    }

    @Override
    public boolean markSent(UUID id) {
        return jdbc.update("UPDATE outbox_message SET status = 'sent', sent_at = now() WHERE id = ? AND status = 'pending'", id) > 0;
    }

    @Override
    public boolean markFailed(UUID id) {
        return jdbc.update("UPDATE outbox_message SET status = 'failed' WHERE id = ? AND status = 'pending'", id) > 0;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
