package com.flora.api.billing.infra;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import com.flora.api.billing.domain.WalletLedgerEntry;
import com.flora.api.billing.domain.WalletLedgerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * wallet_ledger 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>流水只追加：这里没有 update / delete。
 */
@Repository
public class JdbcWalletLedgerRepository implements WalletLedgerRepository {

    private static final String COLUMNS = "id, wallet_id, user_id, type, amount, hold_id, job_id, created_at";

    private static final RowMapper<WalletLedgerEntry> MAPPER = (rs, rowNum) -> new WalletLedgerEntry(
            rs.getObject("id", UUID.class),
            rs.getObject("wallet_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            WalletLedgerType.of(rs.getString("type")),
            rs.getLong("amount"),
            rs.getObject("hold_id", UUID.class),
            rs.getObject("job_id", UUID.class),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcWalletLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public WalletLedgerEntry insert(UUID walletId, UUID userId, WalletLedgerType type, long amount, UUID holdId, UUID jobId) {
        List<WalletLedgerEntry> rows = jdbc.query(
                "INSERT INTO wallet_ledger (wallet_id, user_id, type, amount, hold_id, job_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS,
                ps -> {
                    ps.setObject(1, walletId);
                    ps.setObject(2, userId);
                    ps.setString(3, type.value());
                    ps.setLong(4, amount);
                    setNullableUuid(ps, 5, holdId);
                    setNullableUuid(ps, 6, jobId);
                },
                MAPPER);
        return rows.get(0);
    }

    @Override
    public boolean bindJobId(UUID holdId, UUID jobId) {
        int updated = jdbc.update("UPDATE wallet_ledger SET job_id = ? WHERE hold_id = ? AND job_id IS NULL", jobId, holdId);
        return updated > 0;
    }

    @Override
    public List<WalletLedgerEntry> findLedgerByUserId(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM wallet_ledger WHERE user_id = ? ORDER BY created_at, id",
                MAPPER, userId);
    }

    private static void setNullableUuid(java.sql.PreparedStatement ps, int index, UUID value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
