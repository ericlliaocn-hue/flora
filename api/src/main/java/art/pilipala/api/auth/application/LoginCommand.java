package art.pilipala.api.auth.application;

/**
 * 登录用例入参。不依赖 HTTP 类型，Controller 负责转换。
 */
public record LoginCommand(String username, String password) {
}
