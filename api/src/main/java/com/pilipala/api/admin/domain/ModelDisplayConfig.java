package com.pilipala.api.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 模型展示配置：只管前端展示与上下架，不是渠道密钥表，也不是额度账本（ADR 0001）。
 *
 * @param sortOrder 排序位；小的排前面
 */
public record ModelDisplayConfig(
        UUID id,
        String model,
        String capability,
        String displayName,
        boolean enabled,
        int sortOrder,
        Instant updatedAt) {
}
