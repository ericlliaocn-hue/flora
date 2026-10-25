package com.pilipala.api.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * idempotency_record 行：{@code (user_id, key)} → 首次 submit 的 job_id 与请求哈希。
 *
 * <p>同一 key 重复 submit 返回同一 job_id；同 key 不同 body → IDEMPOTENCY_CONFLICT。
 */
public record IdempotencyRecord(
        UUID id,
        UUID userId,
        String key,
        UUID jobId,
        String requestHash,
        Instant createdAt) {
}
