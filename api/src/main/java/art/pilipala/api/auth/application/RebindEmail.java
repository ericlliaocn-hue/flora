package art.pilipala.api.auth.application;

import java.util.UUID;

import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class RebindEmail {

    private final UserRepository users;
    private final AuthSessionService sessions;
    private final OtpService otpService;

    public RebindEmail(UserRepository users, AuthSessionService sessions, OtpService otpService) {
        this.users = users;
        this.sessions = sessions;
        this.otpService = otpService;
    }

    public void rebind(String newEmailRaw, String otp) {
        String newEmail = IdentityNormalizer.normalizeEmail(newEmailRaw);
        if (newEmail == null) {
            throw AuthException.validation();
        }
        User current = currentUser();
        if (users.findByEmail(newEmail).filter(other -> !other.id().equals(current.id())).isPresent()) {
            throw AuthException.identityOccupied();
        }
        if (!otpService.verify(OtpPurpose.REBIND_EMAIL, newEmail, otp)) {
            throw AuthException.otpInvalid();
        }
        users.updateEmail(current.id(), newEmail);
        otpService.consume(OtpPurpose.REBIND_EMAIL, newEmail);
    }

    private User currentUser() {
        UUID userId = UUID.fromString(sessions.currentUserId());
        return users.findById(userId).orElseThrow(AuthException::unauthorized);
    }
}
