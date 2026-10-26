package art.pilipala.api.settings.domain;

import java.util.Set;

/**
 * {@code app_runtime_setting} 中与认证相关的已知 key。
 */
public final class AuthRuntimeKeys {

    public static final String EMAIL_LOGIN_ENABLED = "auth.email_login_enabled";
    public static final String EMAIL_REGISTER_ENABLED = "auth.email_register_enabled";

    public static final Set<String> KNOWN_KEYS = Set.of(EMAIL_LOGIN_ENABLED, EMAIL_REGISTER_ENABLED);

    private AuthRuntimeKeys() {
    }
}
