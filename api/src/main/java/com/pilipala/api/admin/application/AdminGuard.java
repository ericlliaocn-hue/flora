package com.pilipala.api.admin.application;

import java.util.UUID;

import com.pilipala.api.auth.infra.AuthException;
import com.pilipala.api.auth.infra.AuthSessionService;
import com.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Component;

/**
 * 运维后台的权限闸门。
 *
 * <p>登录校验由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}：
 * 未登录时已经在闸门外被拦掉（UNAUTHORIZED）。这里只做第二道：
 * 已登录但角色不是 admin 的用户一律拒绝（FORBIDDEN）。
 *
 * <p>角色每次都从库里读：管理员降权后下一次请求就失效，不依赖会话里的旧值。
 */
@Component
public class AdminGuard {

    private final AuthSessionService sessions;
    private final UserRepository users;

    public AdminGuard(AuthSessionService sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    /**
     * 要求当前登录者是管理员，否则抛异常。
     *
     * @throws AuthException 未登录（UNAUTHORIZED）或不是管理员（FORBIDDEN）
     */
    public void requireAdmin() {
        UUID userId = currentUserId();
        String role = users.findById(userId).map(user -> user.role()).orElseThrow(AuthException::unauthorized);
        if (!"admin".equals(role)) {
            throw AuthException.forbidden();
        }
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }
}
