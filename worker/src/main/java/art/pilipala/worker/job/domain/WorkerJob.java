package art.pilipala.worker.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * worker 看到的最小 job 视图。
 *
 * <p>刻意不含队列字段、画布字段、上游原文：worker 只读任务、只写状态机
 * （ADR 0003 / ADR 0004）。
 *
 * @param paramsJson 已物化的参数（JSON 文本）
 * @param productId  WP21 产品 SKU；老 job 为空
 * @param targetId   Phase 1 旧 target；Phase 2 起可为空
 * @param gatewayGroup Phase 2 NewAPI group；为空走 Anytoken 存量路径
 * @param gatewayModel Phase 2 NewAPI model
 * @param gatewayTimeoutSeconds Phase 2 task deadline 秒数
 */
public record WorkerJob(
        UUID id,
        WorkerJobStatus status,
        String capability,
        String model,
        UUID productId,
        UUID targetId,
        String gatewayGroup,
        String gatewayModel,
        Integer gatewayTimeoutSeconds,
        String paramsJson,
        int attempt,
        int maxAttempts,
        Instant cancelRequestedAt,
        String errorCode,
        Instant updatedAt) {
}
