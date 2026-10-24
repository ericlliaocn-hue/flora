package com.flora.api.auth.infra;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码哈希：BCrypt，strength 10。
 *
 * <p>只引入 spring-security-crypto，不拉起整套 Spring Security 过滤链——
 * 认证由 Sa-Token 负责，见 {@link SaTokenConfig}。
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    /** 等时校验用的假哈希：用户不存在时也走一次 BCrypt，避免用耗时差异暴露「用户是否存在」。 */
    private final String equalizerHash = encoder.encode("flora-login-equalizer");

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    /**
     * 用户不存在时调用，抹平与「密码错误」的耗时差异。结果不参与判断。
     */
    public void matchEqualizer(String rawPassword) {
        encoder.matches(rawPassword, equalizerHash);
    }
}
