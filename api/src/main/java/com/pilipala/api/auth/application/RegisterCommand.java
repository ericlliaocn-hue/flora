package com.pilipala.api.auth.application;

/**
 * 注册用例入参。不依赖 HTTP 类型，Controller 负责转换。
 */
public record RegisterCommand(String username, String password, String locale) {
}
