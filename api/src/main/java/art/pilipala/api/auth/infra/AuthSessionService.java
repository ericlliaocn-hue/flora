package art.pilipala.api.auth.infra;

import cn.dev33.satoken.stp.StpUtil;
import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.domain.User;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 会话读写。会话存 Redis（见 sa-token-redis-jackson 依赖）。
 *
 * <p>应用层只依赖这里的语义，不直接碰 {@link StpUtil}。
 */
@Component
public class AuthSessionService {

    /**
     * 开启会话。loginId 是 {@code users.id} 的 UUID 字符串。
     */
    public AuthSession start(User user) {
        StpUtil.login(user.id().toString());
        return new AuthSession(user.id(), user.username(), user.locale(), StpUtil.getTokenValue(), user.role());
    }

    /**
     * 当前登录用户的 id；调用前应已通过鉴权闸门。
     */
    public String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 结束当前会话，令牌立即失效。
     */
    public void end() {
        StpUtil.logout();
    }
}
