package com.pilipala.api.admin.infra;

import org.springframework.stereotype.Component;

import com.pilipala.api.auth.domain.User;
import com.pilipala.api.auth.infra.UserRepository;

/**
 * 初始管理员引导：命中 {@code flora.admin.bootstrap-usernames} 的用户名自动升为 admin。
 *
 * <p>这样本地联调与自动化验收只要注册或登录 {@code admin}（或 {@code flora_admin}）
 * 就能拿到运维权限，不必手写 SQL 改库。已经在库里是 admin 的用户不会被降权。
 */
@Component
public class AdminBootstrap {
    private final UserRepository users;
    private final AdminProperties properties;

    public AdminBootstrap(UserRepository users, AdminProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    /**
     * 返回生效角色后的用户。
     *
     * <p>命中引导名单且当前不是 admin 时，先把库里的 role 改成 admin，再返回带 admin 的用户。
     */
    public User ensureRole(User user) {
        if ("admin".equals(user.role()) || !properties.isBootstrapAdmin(user.username())) {
            return user;
        }
        users.updateRole(user.id(), "admin");
        return new User(user.id(), user.username(), user.passwordHash(), user.locale(), user.createdAt(), user.updatedAt(), "admin");
    }
}
