package art.pilipala.api.i18n.application;

import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 请求语言决策：登录用户偏好（{@code users.locale}）→ {@code Accept-Language} → {@code zh}。
 *
 * <p>查用户或读会话失败只回退，不抛异常：语言解析不该把主流程带崩。
 */
@Component
public class RequestLocaleResolver {

    private static final Logger log = LoggerFactory.getLogger(RequestLocaleResolver.class);

    private final AuthSessionService sessions;
    private final UserRepository users;

    public RequestLocaleResolver(AuthSessionService sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    /** 当前请求用哪种语言。 */
    public String resolve(HttpServletRequest request) {
        return resolve(currentUserId(), request == null ? null : request.getHeader("Accept-Language"));
    }

    /** 已知 userId 时按用户偏好优先；未登录或查不到偏好时按请求头解析。 */
    public String resolve(UUID userId, String acceptLanguage) {
        if (userId != null) {
            try {
                User user = users.findById(userId).orElse(null);
                if (user != null && ("zh".equals(user.locale()) || "en".equals(user.locale()))) {
                    return user.locale();
                }
            } catch (RuntimeException e) {
                log.debug("读 users.locale 失败，回退 Accept-Language user={}", userId);
            }
        }
        return I18nDefaultCatalog.localeOf(acceptLanguage);
    }

    /** 当前会话的 user id；未登录或 id 非法时返回 null。 */
    private UUID currentUserId() {
        try {
            String raw = sessions.currentUserId();
            return raw == null ? null : UUID.fromString(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
