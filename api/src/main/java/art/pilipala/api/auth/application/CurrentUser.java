package art.pilipala.api.auth.application;

import java.util.UUID;

/** 当前登录用户（WP17：脱敏身份，省略 username）。 */
public record CurrentUser(UUID userId, String phone, String email, String locale, String role) {
}
