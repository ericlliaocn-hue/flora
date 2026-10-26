package art.pilipala.api.auth.application;

import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.settings.application.RuntimeSettingService;
import art.pilipala.api.settings.domain.AuthRuntimeKeys;
import org.springframework.stereotype.Service;

/**
 * 邮箱通道是否可用：读 {@code app_runtime_setting}，默认关。
 */
@Service
public class EmailAuthPolicy {

    private final RuntimeSettingService settings;

    public EmailAuthPolicy(RuntimeSettingService settings) {
        this.settings = settings;
    }

    public boolean emailLoginEnabled() {
        return settings.getBoolean(AuthRuntimeKeys.EMAIL_LOGIN_ENABLED, false);
    }

    public boolean emailRegisterEnabled() {
        return settings.getBoolean(AuthRuntimeKeys.EMAIL_REGISTER_ENABLED, false);
    }

    public AuthFeaturesView features() {
        return new AuthFeaturesView(emailLoginEnabled(), emailRegisterEnabled());
    }

    public void assertRegisterWithEmail() {
        if (!emailRegisterEnabled()) {
            throw AuthException.emailRegisterDisabled();
        }
    }

    public void assertLoginWithEmail() {
        if (!emailLoginEnabled()) {
            throw AuthException.emailLoginDisabled();
        }
    }

    public void assertEmailOtp(OtpPurpose purpose) {
        switch (purpose) {
            case REGISTER -> assertRegisterWithEmail();
            case LOGIN, RESET_PASSWORD -> assertLoginWithEmail();
            case REBIND_EMAIL -> {
                if (!emailLoginEnabled() && !emailRegisterEnabled()) {
                    throw AuthException.emailLoginDisabled();
                }
            }
            case REBIND_PHONE -> throw AuthException.validation();
            default -> throw AuthException.validation();
        }
    }

    public record AuthFeaturesView(boolean emailLoginEnabled, boolean emailRegisterEnabled) {
    }
}
