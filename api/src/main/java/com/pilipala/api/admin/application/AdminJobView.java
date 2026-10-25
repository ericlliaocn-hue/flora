package com.pilipala.api.admin.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 运维后台看到的任务行，含步骤与上游留痕。
 *
 * <p>ADR 0005：{@code rawStatus} / {@code rawBody} 只允许出现在
 * {@code /api/v1/admin/**} 的响应里，C 端 {@code /api/v1/jobs/**} 严禁带这两个字段。
 *
 * @param prompt 从 job.params 里提取的 prompt；没有就是空串
 * @param holdStatus 关联冻结单的状态；没有冻结单时为 null
 */
public record AdminJobView(
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
        Instant updatedAt,
        List<Step> steps,
        List<ProviderTaskView> providerTasks) {

    /**
     * 一次 attempt 的步骤。
     *
     * @param providerTaskId 关联的上游留痕；没有就是 null
     */
    public record Step(
            UUID stepId,
            int attempt,
            String status,
            String errorCode,
            UUID providerTaskId,
            Instant startedAt,
            Instant endedAt) {
    }

    /**
     * 上游原始留痕。
     *
     * @param rawStatus 上游原始状态码；没有就是 null
     * @param rawBody 上游原始响应体；没有就是 null
     */
    public record ProviderTaskView(
            UUID id,
            UUID jobStepId,
            String upstreamRequestId,
            String rawStatus,
            JsonNode rawBody,
            Instant createdAt) {
    }
}
