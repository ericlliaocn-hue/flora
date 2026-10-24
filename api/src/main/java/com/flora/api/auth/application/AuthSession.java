package com.flora.api.auth.application;

import java.util.UUID;

/**
 * 注册 / 登录成功后返回的会话信息。
 *
 * @param token Sa-Token 值，客户端之后用请求头 {@code flora-token} 携带
 */
public record AuthSession(UUID userId, String username, String locale, String token) {
}
