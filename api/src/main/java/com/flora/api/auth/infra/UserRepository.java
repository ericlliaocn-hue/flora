package com.flora.api.auth.infra;

import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.auth.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * users 表的仓储。SQL 只在这里，Controller 与应用层都不写 SQL。
 *
 * <p>表结构由 Flyway 的 V1__create_users.sql 建，列与
 * docs/engineering/data-model/README.md 一致。
 */
@Repository
public class UserRepository {

    private static final String COLUMNS = "id, username, password_hash, locale, created_at, updated_at";

    private static final RowMapper<User> MAPPER = (rs, rowNum) -> new User(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("locale"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByUsername(String username) {
        List<User> rows = jdbc.query("SELECT " + COLUMNS + " FROM users WHERE username = ?", MAPPER, username);
        return rows.stream().findFirst();
    }

    public Optional<User> findById(UUID id) {
        List<User> rows = jdbc.query("SELECT " + COLUMNS + " FROM users WHERE id = ?", MAPPER, id);
        return rows.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    /**
     * 插入用户；主键由数据库的 {@code gen_random_uuid()} 默认值生成。
     */
    public User insert(String username, String passwordHash, String locale) {
        List<User> rows = jdbc.query(
                "INSERT INTO users (username, password_hash, locale) VALUES (?, ?, ?) RETURNING " + COLUMNS,
                ps -> {
                    ps.setString(1, username);
                    ps.setString(2, passwordHash);
                    if (locale == null) {
                        ps.setNull(3, Types.VARCHAR);
                    } else {
                        ps.setString(3, locale);
                    }
                },
                MAPPER);
        return rows.get(0);
    }
}
