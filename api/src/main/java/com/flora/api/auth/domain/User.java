package com.flora.api.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户聚合。列与 docs/engineering/data-model/README.md 的 users 预留一致。
 *
 * @param id           主键，也是 Sa-Token 的 loginId
 * @param username     登录名
 * @param passwordHash BCrypt 哈希，绝不出库以外的任何出口
 * @param locale       zh / en 偏好，可空
 */
public record User(UUID id, String username, String passwordHash, String locale, Instant createdAt, Instant updatedAt) {
}
