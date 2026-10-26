package art.pilipala.api.auth.application;

import java.util.UUID;

import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

@Service
public class SendOtp {

    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;
    private final UserRepository users;
    private final AuthSessionService sessions;

    public SendOtp(OtpService otpService, EmailAuthPolicy emailAuthPolicy, UserRepository users,
            AuthSessionService sessions) {
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
        this.users = users;
        this.sessions = sessions;
    }

    public OtpService.SendOtpResult send(String channel, String destination, String purposeRaw) {
        OtpPurpose purpose = OtpPurpose.parse(purposeRaw);
        if (purpose == null) {
            throw AuthException.validation();
        }
        if (purpose.requiresLogin() && !StpUtil.isLogin()) {
            throw AuthException.unauthorized();
        }
        String normalized;
        if ("sms".equals(channel)) {
            normalized = IdentityNormalizer.normalizePhone(destination);
        } else if ("email".equals(channel)) {
            emailAuthPolicy.assertEmailOtp(purpose);
            normalized = IdentityNormalizer.normalizeEmail(destination);
        } else {
            throw AuthException.validation();
        }
        if (normalized == null) {
            throw AuthException.validation();
        }
        assertRecipientAllowed(purpose, channel, normalized);
        return otpService.send(channel, normalized, purpose);
    }

    /**
     * 发码前先核对账号是否存在 / 是否已被占用，避免白发短信或邮件。
     */
    void assertRecipientAllowed(OtpPurpose purpose, String channel, String normalized) {
        boolean sms = "sms".equals(channel);
        switch (purpose) {
            case LOGIN, RESET_PASSWORD -> {
                if (!identityExists(sms, normalized)) {
                    throw AuthException.accountNotFound();
                }
            }
            case REGISTER -> {
                if (identityExists(sms, normalized)) {
                    throw AuthException.alreadyRegistered();
                }
            }
            case REBIND_PHONE -> {
                if (!sms) {
                    throw AuthException.validation();
                }
                assertUnoccupiedByOthers(true, normalized);
            }
            case REBIND_EMAIL -> {
                if (sms) {
                    throw AuthException.validation();
                }
                assertUnoccupiedByOthers(false, normalized);
            }
        }
    }

    private boolean identityExists(boolean phone, String normalized) {
        return phone ? users.existsByPhone(normalized) : users.existsByEmail(normalized);
    }

    private void assertUnoccupiedByOthers(boolean phone, String normalized) {
        UUID currentId = UUID.fromString(sessions.currentUserId());
        boolean occupied = (phone ? users.findByPhone(normalized) : users.findByEmail(normalized))
                .filter(other -> !other.id().equals(currentId))
                .isPresent();
        if (occupied) {
            throw AuthException.identityOccupied();
        }
    }
}
