package art.pilipala.api.auth.infra;

import cn.dev33.satoken.stp.StpUtil;
import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.domain.IdentityMasker;
import art.pilipala.api.auth.domain.User;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionService {

    public AuthSession start(User user) {
        StpUtil.login(user.id().toString());
        return toSession(user, StpUtil.getTokenValue());
    }

    public AuthSession toSession(User user, String token) {
        return new AuthSession(
                user.id(),
                IdentityMasker.maskPhone(user.phone()),
                IdentityMasker.maskEmail(user.email()),
                user.locale(),
                token,
                user.role());
    }

    public String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public void end() {
        StpUtil.logout();
    }
}
