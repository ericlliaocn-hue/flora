package com.flora.api.auth.application;

import java.util.UUID;

/**
 * 当前登录用户。响应里不含 token。
 */
public record CurrentUser(UUID userId, String username, String locale) {
}
