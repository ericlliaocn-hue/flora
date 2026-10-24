package com.flora.api.billing.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.billing.domain.Wallet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * wallet 表的 JDBC 实现。SQL 只在这里。
 *
 * <p>表结构由 Flyway 的 V4__create_wallet_domain.sql 建，列与
 * docs/engineering/data-model/billing.md 一致。
 */
@Repository
public class JdbcWalletRepository implements WalletRepository {

    private static final String COLUMNS = "id, user_id, available, frozen, updated_at";

    private static final RowMapper<Wallet> MAPPER = (rs, rowNum) -> new Wallet(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getLong("available"),
            rs.getLong("frozen"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcWalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Wallet> findWallet(UUID walletId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM wallet WHERE id = ?", MAPPER, walletId).stream().findFirst();
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM wallet WHERE user_id = ?", MAPPER, userId).stream().findFirst();
    }

    @Override
    public Optional<Wallet> insertIfAbsent(UUID userId) {
        List<Wallet> rows = jdbc.query(
                "INSERT INTO wallet (user_id, available, frozen) VALUES (?, 0, 0) "
                        + "ON CONFLICT (user_id) DO NOTHING RETURNING " + COLUMNS,
                MAPPER,
                userId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean reserve(UUID walletId, long amount) {
        int updated = jdbc.update(
                "UPDATE wallet SET available = available - ?, frozen = frozen + ?, updated_at = now() "
                        + "WHERE id = ? AND available >= ?",
                amount, amount, walletId, amount);
        return updated > 0;
    }

    @Override
    public boolean addAvailable(UUID walletId, long amount) {
        int updated = jdbc.update(
                "UPDATE wallet SET available = available + ?, updated_at = now() WHERE id = ?",
                amount, walletId);
        return updated > 0;
    }

    @Override
    public boolean consumeFrozen(UUID walletId, long amount) {
        int updated = jdbc.update(
                "UPDATE wallet SET frozen = frozen - ?, updated_at = now() WHERE id = ? AND frozen >= ?",
                amount, walletId, amount);
        return updated > 0;
    }

    @Override
    public boolean releaseFrozen(UUID walletId, long amount) {
        int updated = jdbc.update(
                "UPDATE wallet SET available = available + ?, frozen = frozen - ?, updated_at = now() "
                        + "WHERE id = ? AND frozen >= ?",
                amount, amount, walletId, amount);
        return updated > 0;
    }
}
