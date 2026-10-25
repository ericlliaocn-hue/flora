package art.pilipala.api.job.domain;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * provider_task 行：上游原文留痕。
 *
 * <p>ADR 0005：{@code rawStatus} 与 {@code rawBody} 只允许出现在
 * {@code /api/v1/admin/**} 的响应里，C 端 {@code /api/v1/jobs/**} 严禁带这两个字段。
 *
 * @param rawBody 上游原始响应体；没有留痕时为 null
 */
public record ProviderTask(
        UUID id,
        UUID jobId,
        UUID jobStepId,
        String upstreamRequestId,
        String rawStatus,
        JsonNode rawBody,
        Instant createdAt) {
}
