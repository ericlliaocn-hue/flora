package com.flora.api.billing.infra;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.billing.domain.JobTerminalState;
import com.flora.api.billing.domain.PendingSettlement;
import com.flora.api.billing.domain.WalletHold;
import com.flora.api.billing.domain.WalletHoldStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * wallet_hold 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>终结冻结单走条件更新 {@code WHERE id = ? AND status = 'frozen'}：
 * 影响 1 行说明这次抢到了终结权，影响 0 行说明别人已经终结过（幂等重放）。
 */
@Repository
public class JdbcWalletHoldRepository implements WalletHoldRepository {

    private static final String COLUMNS = "id, user_id, job_id, amount, status, created_at, finalized_at";

    private static final RowMapper<WalletHold> MAPPER = (rs, rowNum) -> new WalletHold(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getLong("amount"),
            WalletHoldStatus.of(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            toInstant(rs.getTimestamp("finalized_at")));

    private final JdbcTemplate jdbc;

    public JdbcWalletHoldRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WalletHold insert(UUID userId, UUID jobId, long amount) {
        List<WalletHold> rows = jdbc.query(
                "INSERT INTO wallet_hold (user_id, job_id, amount, status) VALUES (?, ?, ?, 'frozen') RETURNING " + COLUMNS,
                ps -> {
                    ps.setObject(1, userId);
                    if (jobId == null) {
                        ps.setNull(2, Types.OTHER);
                    } else {
                        ps.setObject(2, jobId);
                    }
                    ps.setLong(3, amount);
                },
                MAPPER);
        return rows.get(0);
    }

    @Override
    public Optional<WalletHold> findHold(UUID holdId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM wallet_hold WHERE id = ?", MAPPER, holdId).stream().findFirst();
    }

    @Override
    public Optional<WalletHold> finalizeIfFrozen(UUID holdId, WalletHoldStatus target) {
        List<WalletHold> rows = jdbc.query(
                "UPDATE wallet_hold SET status = ?, finalized_at = now() WHERE id = ? AND status = 'frozen' RETURNING " + COLUMNS,
                MAPPER,
                target.value(), holdId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean bindJob(UUID holdId, UUID jobId) {
        int updated = jdbc.update("UPDATE wallet_hold SET job_id = ? WHERE id = ? AND job_id IS NULL", jobId, holdId);
        return updated > 0;
    }

    @Override
    public List<PendingSettlement> findPendingSettlement(UUID userId, int limit) {
        String sql = "SELECT h.id, h.user_id, h.job_id, h.amount, j.status AS job_status "
                + "FROM wallet_hold h JOIN job j ON j.id = h.job_id "
                + "WHERE h.status = 'frozen' AND j.status IN ('succeeded', 'cancelled', 'dead') "
                + (userId == null ? "" : "AND h.user_id = ? ")
                + "ORDER BY h.created_at LIMIT ?";
        Object[] args = userId == null ? new Object[]{limit} : new Object[]{userId, limit};
        return jdbc.query(sql, (rs, rowNum) -> new PendingSettlement(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getLong("amount"),
                JobTerminalState.of(rs.getString("job_status"))), args).stream()
                .filter(pending -> pending.jobState() != null)
                .toList();
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
