package art.pilipala.api.auth.application;

import java.util.Optional;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 登录用例：校验凭证 → 开启会话。
 *
 * <p>「用户不存在」与「密码错误」返回同一个 {@code UNAUTHORIZED}，
 * 不暴露用户是否存在；用户不存在时也走一次等时哈希校验。
 */
@Service
public class LoginUser {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;

    public LoginUser(UserRepository users, PasswordHasher hasher, AuthSessionService sessions, AdminBootstrap adminBootstrap) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
    }

    public AuthSession login(LoginCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();

        if (username.isEmpty() || password.isEmpty()) {
            hasher.matchEqualizer(password);
            throw AuthException.unauthorized();
        }

        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty()) {
            hasher.matchEqualizer(password);
            throw AuthException.unauthorized();
        }

        User user = found.get();
        if (!hasher.matches(password, user.passwordHash())) {
            throw AuthException.unauthorized();
        }
        return sessions.start(adminBootstrap.ensureRole(user));
    }
}
