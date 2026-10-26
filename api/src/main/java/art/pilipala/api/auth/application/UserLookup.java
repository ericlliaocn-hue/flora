package art.pilipala.api.auth.application;

import java.util.Optional;

import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserLookup {

    private final UserRepository users;

    public UserLookup(UserRepository users) {
        this.users = users;
    }

    public Optional<User> byPhoneRaw(String rawPhone) {
        String phone = IdentityNormalizer.normalizePhone(rawPhone);
        if (phone == null) {
            return Optional.empty();
        }
        return users.findByPhone(phone);
    }

    public Optional<User> byEmailRaw(String rawEmail) {
        String email = IdentityNormalizer.normalizeEmail(rawEmail);
        if (email == null) {
            return Optional.empty();
        }
        return users.findByEmail(email);
    }

    public Optional<User> byLoginIdentity(String rawPhone, String rawEmail) {
        boolean hasPhone = rawPhone != null && !rawPhone.isBlank();
        boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
        if (hasPhone && !hasEmail) {
            return byPhoneRaw(rawPhone);
        }
        if (hasEmail && !hasPhone) {
            return byEmailRaw(rawEmail);
        }
        return Optional.empty();
    }
}
