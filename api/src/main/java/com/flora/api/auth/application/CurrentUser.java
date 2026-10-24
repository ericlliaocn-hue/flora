package com.flora.api.auth.application;

import java.util.UUID;

/**
 * 当前登录用户。响应里不含 token。
 *
 * @param role user 或 admin；WP11 起前端据此决定是否展示运维后台入口
 */
public record CurrentUser(UUID userId, String username, String locale, String role) {
    /** 兼容构造器：WP11 之前没有 role，默认普通用户。 */
    public CurrentUser(UUID userId, String username, String locale) {
        this(userId, username, locale, "user");
    }
}
