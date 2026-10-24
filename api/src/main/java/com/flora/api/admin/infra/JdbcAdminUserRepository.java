package com.flora.api.admin.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flora.api.admin.domain.AdminUser;

/**
 * 运维后台用户查询的 JDBC 实现。SQL 只在这里。
 *
 * <p>钱包余额用 LEFT JOIN 取：用户还没有 wallet 行时余额按 0 展示，不影响用户列表。
 */
@Repository
public class JdbcAdminUserRepository implements AdminUserRepository {
    private static final String COLUMNS = "u.id, u.username, u.role, u.locale, u.created_at, u.updated_at, "
            + "COALESCE(w.available, 0) AS available, COALESCE(w.frozen, 0) AS frozen";

    private static final RowMapper<AdminUser> MAPPER = (rs, rowNum) -> new AdminUser(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("role"),
            rs.getString("locale"),
            rs.getLong("available"),
            rs.getLong("frozen"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcAdminUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminUser> findUsers(String query, int limit) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return jdbc.query("SELECT " + COLUMNS + " FROM users u LEFT JOIN wallet w ON w.user_id = u.id "
                    + "ORDER BY u.created_at DESC LIMIT ?", MAPPER, limit);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM users u LEFT JOIN wallet w ON w.user_id = u.id "
                + "WHERE u.username ILIKE ? ORDER BY u.created_at DESC LIMIT ?",
                MAPPER, "%" + keyword + "%", limit);
    }
}
