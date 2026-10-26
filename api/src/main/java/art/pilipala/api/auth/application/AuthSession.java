package art.pilipala.api.auth.application;

import java.util.UUID;

/**
 * 注册 / 登录 / 重置密码成功后返回的会话信息（WP17：脱敏 phone/email，不含 username）。
 */
public record AuthSession(
        UUID userId,
        String phone,
        String email,
        String locale,
        String token,
        String role) {
}
