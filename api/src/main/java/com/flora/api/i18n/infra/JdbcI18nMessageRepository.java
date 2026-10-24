package com.flora.api.i18n.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.i18n.domain.I18nMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * i18n_message 表的 JDBC 实现。所有 SQL 只写在本类内。
 *
 * <p>表结构由 Flyway 的 {@code V5__create_i18n_message.sql} 建，列与
 * docs/engineering/data-model/errors.md 一致。
 */
@Repository
public class JdbcI18nMessageRepository implements I18nMessageRepository {

    private static final String COLUMNS = "id, key, locale, template, updated_at";

    private static final RowMapper<I18nMessage> MAPPER = (rs, rowNum) -> new I18nMessage(
            rs.getObject("id", UUID.class),
            rs.getString("key"),
            rs.getString("locale"),
            rs.getString("template"),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcI18nMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<I18nMessage> findByKeyAndLocale(String key, String locale) {
        List<I18nMessage> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM i18n_message WHERE key = ? AND locale = ?",
                MAPPER, key, locale);
        return rows.stream().findFirst();
    }

    @Override
    public List<I18nMessage> findByKey(String key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM i18n_message WHERE key = ? ORDER BY locale",
                MAPPER, key);
    }

    @Override
    public I18nMessage upsert(String key, String locale, String template) {
        List<I18nMessage> rows = jdbc.query(
                "INSERT INTO i18n_message (key, locale, template, updated_at) VALUES (?, ?, ?, now()) "
                        + "ON CONFLICT (key, locale) DO UPDATE SET template = EXCLUDED.template, updated_at = now() "
                        + "RETURNING " + COLUMNS,
                MAPPER, key, locale, template);
        return rows.get(0);
    }

    @Override
    public List<I18nMessage> search(String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return jdbc.query("SELECT " + COLUMNS + " FROM i18n_message ORDER BY key, locale", MAPPER);
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM i18n_message WHERE key ILIKE ? OR template ILIKE ? "
                + "ORDER BY key, locale", MAPPER, "%" + keyword + "%", "%" + keyword + "%");
    }
}
