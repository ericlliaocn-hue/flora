package art.pilipala.api.auth.application;

import art.pilipala.api.auth.infra.AuthSessionService;
import org.springframework.stereotype.Service;

/**
 * 登出用例：让当前会话立即失效。
 */
@Service
public class LogoutUser {

    private final AuthSessionService sessions;

    public LogoutUser(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    public void logout() {
        sessions.end();
    }
}
