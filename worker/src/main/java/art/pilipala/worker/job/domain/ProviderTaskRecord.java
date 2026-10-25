package art.pilipala.worker.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * provider_task 行：上游原文留痕。
 *
 * <p>只有 worker 写、只有运营看；C 端 query / result 绝不返回这里任何字段
 * （ADR 0005）。
 *
 * @param rawBodyJson 脱敏且截断的上游响应摘要（JSON 文本）
 */
public record ProviderTaskRecord(
        UUID id,
        UUID jobId,
        UUID jobStepId,
        String upstreamRequestId,
        String rawStatus,
        String rawBodyJson,
        Instant createdAt) {
}
