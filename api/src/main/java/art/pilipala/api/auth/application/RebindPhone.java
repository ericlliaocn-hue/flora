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
public class RebindPhone {

    private final UserRepository users;
    private final AuthSessionService sessions;
    private final OtpService otpService;

    public RebindPhone(UserRepository users, AuthSessionService sessions, OtpService otpService) {
        this.users = users;
        this.sessions = sessions;
        this.otpService = otpService;
    }

    public void rebind(String newPhoneRaw, String otp) {
        String newPhone = IdentityNormalizer.normalizePhone(newPhoneRaw);
        if (newPhone == null) {
            throw AuthException.validation();
        }
        User current = currentUser();
        if (users.findByPhone(newPhone).filter(other -> !other.id().equals(current.id())).isPresent()) {
            throw AuthException.identityOccupied();
        }
        if (!otpService.verify(OtpPurpose.REBIND_PHONE, newPhone, otp)) {
            throw AuthException.otpInvalid();
        }
        users.updatePhone(current.id(), newPhone);
        otpService.consume(OtpPurpose.REBIND_PHONE, newPhone);
    }

    private User currentUser() {
        UUID userId = UUID.fromString(sessions.currentUserId());
        return users.findById(userId).orElseThrow(AuthException::unauthorized);
    }
}
