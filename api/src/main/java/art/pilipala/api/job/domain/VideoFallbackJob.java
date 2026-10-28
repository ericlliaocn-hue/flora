package art.pilipala.api.job.domain;

import java.util.UUID;

/** 产品级 fallback 的候选 job（只读投影）。 */
public record VideoFallbackJob(
        UUID id,
        UUID productId,
        UUID targetId,
        String errorCode,
        int attempt,
        int maxAttempts) {
}
