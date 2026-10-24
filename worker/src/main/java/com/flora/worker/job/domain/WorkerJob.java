package com.flora.worker.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * worker 看到的最小 job 视图。
 *
 * <p>刻意不含队列字段、画布字段、上游原文：worker 只读任务、只写状态机
 * （ADR 0003 / ADR 0004）。
 *
 * @param paramsJson 已物化的参数（JSON 文本）
 */
public record WorkerJob(
        UUID id,
        WorkerJobStatus status,
        String capability,
        String model,
        String paramsJson,
        int attempt,
        int maxAttempts,
        Instant cancelRequestedAt,
        String errorCode,
        Instant updatedAt) {
}
