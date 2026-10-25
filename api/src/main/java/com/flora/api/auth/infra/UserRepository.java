package com.flora.api.auth.infra;

import java.util.Optional;
import java.util.UUID;

import com.flora.api.auth.domain.User;
import org.springframework.stereotype.Repository;

/**
 * users 表的仓储。SQL 只在这里，Controller 与应用层都不写 SQL。
 *
 * <p>表结构由 Flyway 的 V1__create_users.sql 建，列与
 * docs/engineering/data-model/README.md 一致。
 */
@Repository
public class UserRepository {

    private final UserMapper mapper;

    public UserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username));
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public boolean existsByUsername(String username) {
        return mapper.countByUsername(username) > 0;
    }

    /**
     * 插入用户；主键由数据库的 {@code gen_random_uuid()} 默认值生成。
     */
    public User insert(String username, String passwordHash, String locale) {
        return mapper.insert(username, passwordHash, locale);
    }

    /**
     * 改角色：WP11 的 bootstrap admin 升权用。
     *
     * <p>只允许 {@code user} / {@code admin} 两个值（DB 有 CHECK 约束兜底）。
     *
     * @return 是否真的改了
     */
    public boolean updateRole(UUID id, String role) {
        return mapper.updateRole(id, role) > 0;
    }
}
