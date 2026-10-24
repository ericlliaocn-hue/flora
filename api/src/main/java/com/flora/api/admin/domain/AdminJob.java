package com.flora.api.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 运维后台看到的任务行（联表 users 与 wallet_hold）。
 *
 * @param prompt    从 job.params 里取的 prompt；没有就是空串
 * @param holdStatus 关联冻结单的状态；没有冻结单时为 null
 */
public record AdminJob(
        UUID jobId,
        UUID userId,
        String username,
        String status,
        String capability,
        String model,
        String prompt,
        int attempt,
        int maxAttempts,
        UUID holdId,
        String holdStatus,
        String errorCode,
        Instant createdAt,
        Instant updatedAt) {
}
