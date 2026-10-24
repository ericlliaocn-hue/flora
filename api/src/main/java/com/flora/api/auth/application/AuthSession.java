package com.flora.api.auth.application;

import java.util.UUID;

/**
 * 注册 / 登录成功后返回的会话信息。
 *
 * @param token Sa-Token 值，客户端之后用请求头 {@code flora-token} 携带
 * @param role  user 或 admin；WP11 起前端据此决定是否展示运维后台入口
 */
public record AuthSession(UUID userId, String username, String locale, String token, String role) {
    /** 兼容构造器：WP11 之前没有 role，默认普通用户。 */
    public AuthSession(UUID userId, String username, String locale, String token) {
        this(userId, username, locale, token, "user");
    }
}
