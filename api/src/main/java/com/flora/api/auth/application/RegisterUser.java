package com.flora.api.auth.application;

import java.util.Set;
import java.util.regex.Pattern;

import com.flora.api.auth.domain.User;
import com.flora.api.auth.infra.AuthException;
import com.flora.api.auth.infra.AuthSessionService;
import com.flora.api.auth.infra.PasswordHasher;
import com.flora.api.auth.infra.UserRepository;
import com.flora.api.billing.application.TopUpWallet;
import org.springframework.stereotype.Service;

/**
 * 注册用例：校验入参 → 写 users 行（BCrypt 哈希）→ 初始化钱包 → 开启会话。
 *
 * <p>规则来自 WP3 brief：用户名 3–32 位 {@code [a-zA-Z0-9_]+}、密码至少 8 位、
 * locale 只能是 zh / en。用户名已存在同样回 VALIDATION_ERROR。
 *
 * <p>WP6 起注册后确保钱包行存在；配置了 {@code flora.billing.initial-credits}
 * 时经充值路径记一笔 topup 流水，不允许只改余额不写流水。
 */
@Service
public class RegisterUser {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> LOCALES = Set.of("zh", "en");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final TopUpWallet topUpWallet;

    public RegisterUser(UserRepository users, PasswordHasher hasher, AuthSessionService sessions, TopUpWallet topUpWallet) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.topUpWallet = topUpWallet;
    }

    public AuthSession register(RegisterCommand command) {
        String username = command.username() == null ? "" : command.username().trim();
        String password = command.password() == null ? "" : command.password();
        String locale = command.locale() == null || command.locale().isBlank() ? null : command.locale().trim();

        if (!USERNAME.matcher(username).matches()) {
            throw AuthException.validation();
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw AuthException.validation();
        }
        if (locale != null && !LOCALES.contains(locale)) {
            throw AuthException.validation();
        }
        if (users.existsByUsername(username)) {
            throw AuthException.validation();
        }

        User user = users.insert(username, hasher.hash(password), locale);
        topUpWallet.ensureInitialWallet(user.id());
        return sessions.start(user);
    }
}
