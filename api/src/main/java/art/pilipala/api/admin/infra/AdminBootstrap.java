package art.pilipala.api.admin.infra;

import org.springframework.stereotype.Component;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.UserRepository;

@Component
public class AdminBootstrap {
    private final UserRepository users;
    private final AdminProperties properties;

    public AdminBootstrap(UserRepository users, AdminProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    public User ensureRole(User user) {
        if ("admin".equals(user.role()) || !shouldPromote(user)) {
            return user;
        }
        users.updateRole(user.id(), "admin");
        return new User(user.id(), user.username(), user.phone(), user.email(), user.passwordHash(), user.locale(),
                user.createdAt(), user.updatedAt(), "admin");
    }

    private boolean shouldPromote(User user) {
        if (user.username() != null && properties.isBootstrapAdminUsername(user.username())) {
            return true;
        }
        if (user.phone() != null && properties.isBootstrapAdminPhone(user.phone())) {
            return true;
        }
        return user.email() != null && properties.isBootstrapAdminEmail(user.email());
    }
}
