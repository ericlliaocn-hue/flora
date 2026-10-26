package art.pilipala.api.auth.application;

import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ChangePassword {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;

    public ChangePassword(UserRepository users, PasswordHasher hasher, AuthSessionService sessions) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
    }

    public void change(String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw AuthException.validation();
        }
        User user = currentUser();
        if (oldPassword == null || !hasher.matches(oldPassword, user.passwordHash())) {
            throw AuthException.validation();
        }
        users.updatePasswordHash(user.id(), hasher.hash(newPassword));
    }

    private User currentUser() {
        UUID userId = UUID.fromString(sessions.currentUserId());
        return users.findById(userId).orElseThrow(AuthException::unauthorized);
    }
}
