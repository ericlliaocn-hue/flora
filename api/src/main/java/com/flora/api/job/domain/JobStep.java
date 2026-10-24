package com.flora.api.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * job_step 行：一次 attempt 一行。
 *
 * @param providerTaskId 指向 provider_task.id；WP5 之前恒为 null（本 WP 不建物理 FK）
 */
public record JobStep(
        UUID id,
        UUID jobId,
        int attempt,
        JobStepStatus status,
        String errorCode,
        UUID providerTaskId,
        Instant startedAt,
        Instant endedAt) {
}
