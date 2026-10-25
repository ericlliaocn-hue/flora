package art.pilipala.api.auth.web;

import java.util.Map;

import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.application.CurrentUser;
import art.pilipala.api.auth.application.GetCurrentUser;
import art.pilipala.api.auth.application.LoginCommand;
import art.pilipala.api.auth.application.LoginUser;
import art.pilipala.api.auth.application.LogoutUser;
import art.pilipala.api.auth.application.RegisterCommand;
import art.pilipala.api.auth.application.RegisterUser;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端认证接口。
 *
 * <p>{@code /register} 与 {@code /login} 在鉴权闸门的放行名单里；
 * {@code /logout} 与 {@code /me} 需要有效会话。
 *
 * <p>失败一律返回统一信封（{@code ok=false} + {@code error_code} + {@code message}），
 * HTTP 状态码恒为 200：失败信号看 {@code ok} 与 {@code error_code}。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUser registerUser;
    private final LoginUser loginUser;
    private final LogoutUser logoutUser;
    private final GetCurrentUser getCurrentUser;

    public AuthController(RegisterUser registerUser, LoginUser loginUser, LogoutUser logoutUser, GetCurrentUser getCurrentUser) {
        this.registerUser = registerUser;
        this.loginUser = loginUser;
        this.logoutUser = logoutUser;
        this.getCurrentUser = getCurrentUser;
    }

    @PostMapping("/register")
    public ApiResponse<AuthSession> register(@RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(request.username(), request.password(), request.locale());
        return ApiResponse.ok(registerUser.register(command));
    }

    @PostMapping("/login")
    public ApiResponse<AuthSession> login(@RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.username(), request.password());
        return ApiResponse.ok(loginUser.login(command));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        logoutUser.logout();
        return ApiResponse.ok(Map.of());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me() {
        return ApiResponse.ok(getCurrentUser.current());
    }

    /** 注册请求体。 */
    public record RegisterRequest(String username, String password, String locale) {
    }

    /** 登录请求体。 */
    public record LoginRequest(String username, String password) {
    }
}
