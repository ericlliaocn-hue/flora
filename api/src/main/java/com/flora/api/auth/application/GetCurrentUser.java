package com.flora.api.auth.application;

import java.util.UUID;

import com.flora.api.auth.domain.User;
import com.flora.api.auth.infra.AuthException;
import com.flora.api.auth.infra.AuthSessionService;
import com.flora.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 读取当前登录用户。会话有效但用户行已不存在时同样按未登录处理。
 */
@Service
public class GetCurrentUser {

    private final UserRepository users;
    private final AuthSessionService sessions;

    public GetCurrentUser(UserRepository users, AuthSessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public CurrentUser current() {
        String loginId = sessions.currentUserId();
        UUID userId;
        try {
            userId = UUID.fromString(loginId);
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
        User user = users.findById(userId).orElseThrow(AuthException::unauthorized);
        return new CurrentUser(user.id(), user.username(), user.locale(), user.role());
    }
}
